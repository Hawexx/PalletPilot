# PalletPilot WCS — Load / Soak Test

## Prerequisites

```bash
# Install k6
brew install k6

# Backend must be running
cd backend && SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

## Quick Start

### CI Mode (5 minutes — fast validation)
```bash
k6 run --env MODE=ci tools/loadtest/soak.js
```

### Soak Mode (4 hours — memory leak detection)
```bash
k6 run --env MODE=soak tools/loadtest/soak.js
```

### Custom Base URL
```bash
k6 run --env MODE=ci --env BASE_URL=http://staging:8080 tools/loadtest/soak.js
```

## Scenarios

| Scenario | CI Rate | Soak Rate | Duration |
|----------|---------|-----------|----------|
| Movu Webhook | 10 req/s | 30 req/s | 5m / 4h |
| ERP Webhook | 5 req/s | 20 req/s | 5m / 4h |
| API Browse | 5 req/s | 10 req/s | 5m / 4h |

## What It Tests

- **Rate limit eviction**: 1000+ unique IPs → map should plateau, not grow
- **Webhook throughput**: sustained load with 10% error injection
- **Memory stability**: heap must plateau within ~30 min (soak mode)
- **429 behaviour**: rate limiter returns correct headers

## Metrics to Watch During Soak

```bash
# Prometheus queries (Grafana or direct)
palletpilot_ratelimit_windows_size           # Should plateau < 200
palletpilot_movu_webhook_rate_buckets_size   # Should plateau < 100
palletpilot_movu_remediation_alert_dedupe_size # Should plateau < 50
jvm_memory_used_bytes{area="heap"}           # Must plateau
hikaricp_connections_active                   # Must stay < max-pool-size
```

## Pass/Fail Criteria

| Metric | Pass | Fail |
|--------|------|------|
| p95 latency | < 1000ms | > 1000ms |
| Error rate | < 5% | > 5% |
| Heap (soak end vs. start) | < 2x growth | Monotonic increase |
| Map gauge sizes | Plateau | Monotonic increase |
