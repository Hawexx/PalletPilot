# PalletPilot WCS — Field Deployment Checklist

**Version:** 1.0 | **Date:** 2026-03-01

---

## Pre-Deployment

### Network
- [ ] VLAN configured for PLC network (isolated from office LAN)
- [ ] Firewall rules: allow TCP 48898 (ADS), 851 (TwinCAT runtime), 30002 (KUKA)
- [ ] NTP sync verified between WCS server and PLC (±500ms tolerance)
- [ ] Static IPs assigned to all equipment

### TwinCAT / Beckhoff
- [ ] ADS route added: PLC → WCS server (bidirectional)
- [ ] AMS Net ID configured in Equipment.configJson
- [ ] Symbol path (GVL) matches configJson `"symbolPath"`
- [ ] TwinCAT runtime in RUN mode
- [ ] ST_WcsCommand / ST_WcsStatus structs deployed to PLC
- [ ] FB_WcsEquipmentHandler instantiated for each equipment

### KUKA
- [ ] TCP server running on KUKA controller (port 30002)
- [ ] KRL program loaded and active
- [ ] Gripper I/O verified
- [ ] Safety zones configured

### Conveyor / Wrapper
- [ ] PLC tags mapped for each conveyor/wrapper
- [ ] Speed limits configured in configJson
- [ ] Safety interlocks wired and tested

---

## Equipment Registration

For each equipment, verify in PalletPilot DB:
```
+-----------+-----------+-----------+----------+---------------------------+
| Name      | IP        | Port      | Protocol | ConfigJson                |
+-----------+-----------+-----------+----------+---------------------------+
| KUKA-01   | 10.0.1.10 | 30002     | TCP      | {"timeoutMs":30000,...}    |
| CONV-01   | 10.0.1.20 | 48898     | ADS      | {"adsNetId":"...","sym..."}|
| WRAPPER-1 | 10.0.1.20 | 48898     | ADS      | {"adsNetId":"...","sym..."}|
+-----------+-----------+-----------+----------+---------------------------+
```

---

## Smoke Tests

### 1. Connection Test
```bash
# Test each equipment connection
curl -X POST http://localhost:8080/api/equipment/{id}/test-connection
# Expected: {"connected": true}
```

### 2. Simulation Mode Test
```bash
# Enable simulation
curl -X PUT http://localhost:8080/api/settings -d '{"key":"app.simulation.enabled","value":"true"}'

# Start a journey
curl -X POST "http://localhost:8080/api/orchestration/start?palletId=<UUID>&journeyType=DEFAULT"

# Advance
curl -X POST "http://localhost:8080/api/orchestration/<journeyId>/advance"

# Check status
curl http://localhost:8080/api/orchestration/<journeyId>/status
```

### 3. Error Injection Test
```bash
# Inject error on equipment
curl -X POST "http://localhost:8080/api/sim/inject-error?equipmentId=<UUID>&errorCode=100"

# Advance journey (should trigger compensation)
curl -X POST "http://localhost:8080/api/orchestration/<journeyId>/advance"
```

### 4. Safety Test
```bash
# Verify E-Stop blocks commands
curl -X POST "http://localhost:8080/api/system-state/transition" \
  -d '{"newState":"EMERGENCY_STOP","userId":"TEST","reason":"E-Stop test"}'

# Try command (should be rejected)
curl -X POST "http://localhost:8080/api/equipment/<id>/command" -d '{"command":"PICK"}'
# Expected: 403/400 with safety message

# Reset
curl -X POST "http://localhost:8080/api/system-state/transition" \
  -d '{"newState":"STOPPED","userId":"TEST","reason":"Reset"}'
curl -X POST "http://localhost:8080/api/system-state/transition" \
  -d '{"newState":"RUNNING","userId":"TEST","reason":"Resume"}'
```

---

## Go-Live Criteria

- [ ] All equipment showing IDLE in dashboard
- [ ] Heartbeats received from PLC (< 5s interval)
- [ ] Simulation mode disabled (`app.simulation.enabled=false`)
- [ ] At least 3 successful pallet journeys completed in staging
- [ ] E-Stop test passed (system correctly enters EMERGENCY_STOP)
- [ ] Interlock test passed (CRITICAL alert raised, equipment-local only)
- [ ] Operator retry/cancel flow verified
- [ ] Audit logs contain all state transitions
- [ ] Monitoring dashboards showing command latency metrics
- [ ] Rollback plan documented and reviewed
