# PalletPilot WCS — Production Readiness Checklist

## Go/No-Go Checklist

### 1. Graceful Shutdown
```bash
# Start the app, then send SIGTERM
kill -15 $(pgrep -f palletpilot)

# Expected log output:
#   Commencing graceful shutdown. Waiting for active requests to complete.
#   Graceful shutdown complete.

# Verify: no 502/503 errors during drain window (30s in prod)
# Verify: no ERROR logs from scheduled tasks after shutdown signal
```

### 2. Actuator Security
```bash
# In prod: only health, metrics, info, prometheus exposed
curl -s https://prod.example/actuator/env
# Expected: 404 Not Found (not exposed)

curl -s https://prod.example/actuator/health
# Expected: {"status":"UP"} (no component details without API key)

curl -s -H "X-Api-Key: <key>" https://prod.example/actuator/health
# Expected: full component details (DB, diskSpace, etc.)
```

### 3. Rate Limiting
```bash
# Send maxRequests+1 requests in <60s
for i in $(seq 1 101); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/orders
done | sort | uniq -c

# Expected:
#   100  200
#     1  429
```

### 4. Webhook Authentication (prod profile)
```bash
# Missing token → 401
curl -s -X POST http://prod:8080/api/movu/callback \
  -H "Content-Type: application/json" \
  -d '{"missionId":"test"}'
# Expected: 401 {"error":"Missing webhook token"}

# Invalid token → 401
curl -s -X POST http://prod:8080/api/movu/callback \
  -H "X-Movu-Webhook-Token: invalid" \
  -H "Content-Type: application/json" \
  -d '{"missionId":"test"}'
# Expected: 401 {"error":"Invalid webhook token"}
```

### 5. Database Pool Health
```bash
# Check active connections via Prometheus
curl -s http://localhost:8080/actuator/prometheus | grep hikaricp_connections_active
# Expected: value < maximum-pool-size (5 dev, 15 prod)

# Check via PostgreSQL
psql -U postgres -d palletpilot -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname='palletpilot';"
# Expected: ≤ max-pool-size
```

### 6. Correlation ID Flow
```bash
# Trigger a 500 error (if test endpoint available) or force one
# Check response body has traceId matching log correlationId:
#   Log:  [500] Unhandled exception on /api/test — traceId=abc12345
#   Body: {"traceId":"abc12345", ...}
```

### 7. Eviction Schedules Running
```bash
# After 10+ minutes of uptime with traffic:
grep "Evicted.*stale" /var/log/palletpilot/*.log
# Expected: "[RATE_LIMIT] Evicted X stale IP entries"
#           "[MOVU_AUTH] Evicted X stale rate buckets"
#           "[REMEDIATION] Evicted X stale alert dedupe entries"
```

### 8. Micrometer Gauges Present
```bash
curl -s http://localhost:8080/actuator/prometheus | grep palletpilot
# Expected output includes:
#   palletpilot_ratelimit_windows_size
#   palletpilot_movu_webhook_rate_buckets_size
#   palletpilot_movu_remediation_alert_dedupe_size
#   palletpilot_orders_created_total
#   palletpilot_commands_executed_total
```

### 9. Multi-Pod Safety
```bash
# Outbox: verify SELECT ... FOR UPDATE SKIP LOCKED
# Run 2 instances simultaneously — no duplicate ERP sends
psql -U postgres -d palletpilot -c \
  "SELECT id, status, attempt_count FROM erp_outbox_messages WHERE status='SENT' GROUP BY id HAVING count(*) > 1;"
# Expected: 0 rows (no duplicates)
```

### 10. XFF Hardening
```bash
# Dev (use-forwarded-headers=false): XFF ignored
curl -H "X-Forwarded-For: 1.2.3.4" http://localhost:8080/api/orders
# Rate limit tracks remoteAddr (127.0.0.1), NOT 1.2.3.4

# Prod: only trusted proxies honoured
# Non-trusted source with XFF → remoteAddr used (ip spoofing blocked)
```

---

## Alerting Configuration

| Alert | Severity | Condition |
|-------|----------|-----------|
| Heap monotonic increase >1h | CRITICAL | `increase(jvm_memory_used_bytes[1h]) > 0` for 60m |
| DB pool saturated | CRITICAL | `hikaricp_connections_active == hikaricp_connections_max` |
| ERP DLQ message | CRITICAL | `count(erp_outbox_messages WHERE status='DLQ') > 0` |
| Rate limit gauge > 500 | WARNING | `palletpilot_ratelimit_windows_size > 500` |
| Circuit breaker OPEN > 5m | WARNING | Resilience4j state metric |
| 429 rate > 5% | WARNING | `rate(http_server_requests_total{status="429"}[5m]) / rate(http_server_requests_total[5m])` |

---

## Deploy Procedure

1. **Pre-deploy**: Run `./gradlew test` — all tests must pass
2. **Rolling deploy**: One pod at a time, 30s drain per pod
3. **Post-deploy**: Verify actuator/health → UP on all pods
4. **Watch** (15 min): Heap, DB pool, gauge sizes, error rate
5. **Rollback trigger**: Any CRITICAL alert within 15 min
