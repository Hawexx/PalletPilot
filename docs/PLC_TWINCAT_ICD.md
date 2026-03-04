# PLC / TwinCAT Interface Control Document (ICD)

**PalletPilot WCS ↔ Beckhoff TwinCAT 3**
**Version:** 1.0 | **Date:** 2026-03-01 | **Status:** Active

---

## 1. Overview

This document defines the formal interface between PalletPilot WCS (Java/Spring Boot) and Beckhoff TwinCAT 3 PLCs via ADS (Automation Device Specification) protocol.

### Communication Pattern
```
WCS (Java)  ──ADS──▶  TwinCAT PLC
   │                      │
   │  1. Write Command     │
   │  (Cmd+SeqId+Valid)    │
   │ ─────────────────────▶│
   │                      │ Parse & Execute
   │  2. Poll Status       │
   │  (AckSeqId+State+     │
   │   ErrorCode+ErrorText)│
   │ ◀─────────────────────│
   │       (200ms poll)    │
```

---

## 2. Tag Schema

Each equipment has a **symbol path** configured in `Equipment.configJson`:
```json
{
  "adsNetId": "192.168.1.20.1.1",
  "symbolPath": "GVL_Kuka"
}
```

### 2.1 Command Struct — `{symbolPath}.Command`

| Field | PLC Type | Java Type | Description |
|-------|----------|-----------|-------------|
| `Cmd` | STRING(255) | String | Command string (e.g., `PICK:KUKA:ARM-1:LOC-A1`) |
| `SeqId` | LINT (INT64) | long | Monotonically increasing sequence ID |
| `Valid` | BOOL | boolean | Set TRUE to trigger execution |

### 2.2 Status Struct — `{symbolPath}.Status`

| Field | PLC Type | Java Type | Description |
|-------|----------|-----------|-------------|
| `AckSeqId` | LINT (INT64) | long | SeqId of the command being acknowledged |
| `State` | INT | int | Current state (see §3) |
| `ErrorCode` | INT | int | Equipment-specific error code (0 = none) |
| `ErrorText` | STRING(255) | String | Human-readable error description |
| `ProgressPct` | INT | int | 0–100 progress percentage (optional) |
| `LastCmd` | STRING(255) | String | Echo of last received command (diagnostic) |

---

## 3. State Machine

```
  ┌─────────────────────────────────┐
  │         0 = IDLE                │
  │  (Ready to accept commands)     │
  └──────────┬──────────────────────┘
             │ Valid=TRUE && SeqId changed
             ▼
  ┌──────────────────────────────────┐
  │         1 = BUSY                 │
  │  (Executing command)             │
  │  AckSeqId = received SeqId       │
  └──────┬──────────┬────────────────┘
         │          │
    Success      Failure
         │          │
         ▼          ▼
  ┌──────────┐  ┌──────────────────┐
  │ 2 = DONE │  │ 3 = ERROR        │
  │          │  │ ErrorCode != 0   │
  │          │  │ ErrorText set    │
  └────┬─────┘  └────┬─────────────┘
       │              │
       │  Done-hold   │  After WCS reads error
       │  (500ms)     │  or operator reset
       ▼              ▼
  ┌─────────────────────────────────┐
  │         0 = IDLE                │
  └─────────────────────────────────┘

  ┌─────────────────────────────────┐
  │      4 = EMERGENCY_STOP         │
  │  (Hardware E-Stop engaged)      │
  │  → WCS MUST stop all commands   │
  │  → WCS sets SystemState=E_STOP  │
  └─────────────────────────────────┘

  ┌─────────────────────────────────┐
  │      5 = INTERLOCK               │
  │  (Safety guard open / interlock) │
  │  → WCS fail-fast, raise alert   │
  │  → No commands until cleared    │
  └─────────────────────────────────┘
```

### State Constants
| Value | Name | WCS Action |
|-------|------|------------|
| 0 | IDLE | Ready — may send commands |
| 1 | BUSY | Polling — wait for completion |
| 2 | DONE | Success — read results, clear Valid |
| 3 | ERROR | Failure — read ErrorCode/Text, raise Alert |
| 4 | EMERGENCY_STOP | Safety — transition system to EMERGENCY_STOP, block all commands |
| 5 | INTERLOCK | Safety — fail-fast, raise CRITICAL alert, actionable error to operator |

---

## 4. Timing Requirements

| Parameter | Default | Configurable Via |
|-----------|---------|-----------------|
| Poll interval | 200ms | `hardware.plc.poll-interval-ms` |
| Command timeout – KUKA PICK/DROP | 30,000ms | Equipment configJson `"timeoutMs"` |
| Command timeout – Conveyor START/STOP | 10,000ms | Equipment configJson `"timeoutMs"` |
| Command timeout – Wrapper WRAP | 60,000ms | Equipment configJson `"timeoutMs"` |
| Command timeout – Generic/Default | 5,000ms | `hardware.plc.default-timeout-ms` |
| Done-hold duration (PLC side) | 500ms | PLC configuration |
| ADS connection timeout | 3,000ms | `hardware.beckhoff.timeout-ms` |

---

## 5. Idempotency Rules

### WCS Side
1. **SeqId monotonically increases** — never reuse same SeqId for same equipment within a 24-hour window.
2. **Idempotency key** = `equipmentId + commandType + businessKey` (e.g., palletId). If same key exists in `command_logs` within TTL, reject as duplicate.
3. **On timeout**: DO NOT auto-retry non-idempotent commands (PICK, WRAP). Only retry idempotent commands (status queries).

### PLC Side
1. **Duplicate SeqId detection**: If `AckSeqId == received SeqId` AND state is DONE or ERROR, PLC MUST NOT re-execute. Simply echo existing status.
2. **Valid flag**: PLC only processes when `Valid = TRUE` AND `SeqId != AckSeqId`.
3. After returning to IDLE, PLC resets `Valid = FALSE` internally.

---

## 6. Safety Rules

### E-Stop Handling
1. When PLC detects hardware E-Stop → set `Status.State = 4 (EMERGENCY_STOP)`.
2. WCS polls and detects State=4 → immediately calls `systemStateService.transitionTo(EMERGENCY_STOP, "PLC", "Hardware E-Stop detected on " + equipmentName)`.
3. All pending commands are marked FAILED with reason `"EMERGENCY_STOP"`.
4. No new commands accepted until system transitions through STOPPED → RUNNING.

### Interlock / Guard
1. When safety guard open or interlock active → PLC sets `Status.State = 5 (INTERLOCK)`.
2. WCS detects State=5 → raises CRITICAL alert: `"Safety interlock active on {equipment}. Check guard/door."`.
3. Command fails immediately with actionable error message.
4. WCS does NOT set EMERGENCY_STOP for interlocks (they are equipment-local).

---

## 7. Error Codes

### Common Error Codes (All Equipment Types)
| Code | Name | Description | WCS Action |
|------|------|-------------|------------|
| 0 | NONE | No error | — |
| 1 | TIMEOUT | Operation timed out internally | Retry (if idempotent) |
| 2 | INVALID_CMD | Unknown command string | Log + alert, do not retry |
| 3 | POSITION_ERR | Target position unreachable | Alert operator |
| 4 | OVERLOAD | Weight/force limit exceeded | HOLD pallet, alert |
| 5 | COLLISION | Collision detected | E-Stop automatic |
| 10 | SENSOR_FAULT | Sensor reading invalid | Alert, retry once |
| 20 | COMM_LOST | Internal bus communication lost | Alert, retry after delay |
| 99 | UNKNOWN | Unclassified error | Alert operator |

### KUKA-Specific (100–199)
| Code | Name | Description |
|------|------|-------------|
| 100 | GRIP_FAIL | Gripper failed to close/open |
| 101 | POSITION_INACCURATE | Position tolerance exceeded |
| 102 | JOINT_LIMIT | Joint limit reached |

### Conveyor-Specific (200–299)
| Code | Name | Description |
|------|------|-------------|
| 200 | JAM_DETECTED | Conveyor jam/blockage |
| 201 | SPEED_FAULT | Speed sensor failed |

### Wrapper-Specific (300–399)
| Code | Name | Description |
|------|------|-------------|
| 300 | FILM_BREAK | Stretch film broken |
| 301 | FILM_END | Film roll empty |
| 302 | TURNTABLE_FAULT | Turntable motor fault |

---

## 8. Equipment ConfigJson Examples

### KUKA Robot
```json
{
  "adsNetId": "192.168.1.20.1.1",
  "symbolPath": "GVL_Kuka",
  "timeoutMs": 30000,
  "capabilities": ["PICK", "DROP", "HOME"],
  "maxPayloadKg": 120,
  "reachMm": 2500
}
```

### Conveyor
```json
{
  "adsNetId": "192.168.1.20.1.1",
  "symbolPath": "GVL_Conveyor1",
  "timeoutMs": 10000,
  "capabilities": ["START", "STOP", "SET_SPEED"],
  "maxSpeedMps": 2.0,
  "lengthMm": 6000
}
```

### Wrapper (Cyklop)
```json
{
  "adsNetId": "192.168.1.20.1.1",
  "symbolPath": "GVL_Wrapper",
  "timeoutMs": 60000,
  "capabilities": ["WRAP", "SET_PROFILE", "STOP"],
  "profiles": ["STANDARD", "HEAVY", "LIGHT"],
  "maxHeightMm": 2200
}
```

---

## 9. TwinCAT 3 — Structured Text Reference Implementation

### 9.1 Type Definitions

```iec-st
// ST_WcsCommand — Written by WCS
TYPE ST_WcsCommand :
STRUCT
    Cmd       : STRING(255);   // Command string
    SeqId     : LINT;          // Sequence ID (monotonic)
    Valid     : BOOL;          // TRUE = new command pending
END_STRUCT
END_TYPE

// ST_WcsStatus — Read by WCS
TYPE ST_WcsStatus :
STRUCT
    AckSeqId    : LINT;          // SeqId being acknowledged
    State       : INT;           // 0=Idle,1=Busy,2=Done,3=Error,4=EStop,5=Interlock
    ErrorCode   : INT;           // 0 = no error
    ErrorText   : STRING(255);   // Human-readable
    ProgressPct : INT;           // 0..100
    LastCmd     : STRING(255);   // Echo of last command
END_STRUCT
END_TYPE
```

### 9.2 Global Variable List

```iec-st
// GVL_Kuka (example for KUKA equipment)
VAR_GLOBAL
    Command : ST_WcsCommand;
    Status  : ST_WcsStatus;
END_VAR
```

### 9.3 Main Function Block

```iec-st
FUNCTION_BLOCK FB_WcsEquipmentHandler
VAR_INPUT
    bEStop    : BOOL;   // Hardware E-Stop signal
    bInterlock: BOOL;   // Safety guard / interlock signal
END_VAR
VAR_IN_OUT
    Command   : ST_WcsCommand;
    Status    : ST_WcsStatus;
END_VAR
VAR
    lastSeqId  : LINT := 0;
    timer      : TON;
    doneHold   : TON;
    state      : INT := 0;      // Internal state machine
    execTimeMs : TIME := T#5S;  // Default execution time (simulate)
END_VAR

// === Main Logic ===

// Priority 1: Safety checks
IF bEStop THEN
    Status.State := 4;  // EMERGENCY_STOP
    Status.ErrorCode := 5;
    Status.ErrorText := 'Hardware E-Stop engaged';
    RETURN;
END_IF

IF bInterlock THEN
    Status.State := 5;  // INTERLOCK
    Status.ErrorCode := 6;
    Status.ErrorText := 'Safety interlock active - check guard';
    RETURN;
END_IF

// Priority 2: Command processing
CASE state OF

0: // IDLE — waiting for command
    Status.State := 0;
    Status.ErrorCode := 0;
    Status.ErrorText := '';
    Status.ProgressPct := 0;

    // Check for new command
    IF Command.Valid AND Command.SeqId <> lastSeqId THEN
        // New command received
        lastSeqId := Command.SeqId;
        Status.AckSeqId := Command.SeqId;
        Status.LastCmd := Command.Cmd;
        Status.State := 1;  // BUSY
        timer(IN := FALSE);
        timer(IN := TRUE, PT := execTimeMs);
        state := 1;
    ELSIF Command.Valid AND Command.SeqId = lastSeqId THEN
        // Duplicate SeqId — ignore, echo existing status
        // (Already handled: AckSeqId still matches)
    END_IF

1: // BUSY — executing
    Status.State := 1;
    timer(IN := TRUE);

    // Simulate progress
    IF timer.PT > T#0S THEN
        Status.ProgressPct := REAL_TO_INT(
            (TIME_TO_REAL(timer.ET) / TIME_TO_REAL(timer.PT)) * 100.0
        );
    END_IF

    IF timer.Q THEN
        // Execution complete
        // >>> INSERT REAL EQUIPMENT LOGIC HERE <<<
        // For now: always succeed
        Status.State := 2;  // DONE
        Status.ProgressPct := 100;
        Status.ErrorCode := 0;
        doneHold(IN := FALSE);
        doneHold(IN := TRUE, PT := T#500MS);
        state := 2;
    END_IF

2: // DONE — hold for WCS to read
    Status.State := 2;
    doneHold(IN := TRUE);

    IF doneHold.Q THEN
        // Done-hold expired → return to IDLE
        Command.Valid := FALSE;  // Reset valid flag
        state := 0;
    END_IF

END_CASE
```

### 9.4 Error Example (Custom Equipment Logic)

```iec-st
// Replace the "always succeed" block in state 1 with:
IF bGripperClosed THEN
    Status.State := 2;  // DONE - success
ELSE
    Status.State := 3;  // ERROR
    Status.ErrorCode := 100;  // GRIP_FAIL
    Status.ErrorText := 'Gripper failed to close within timeout';
    Command.Valid := FALSE;
    state := 0;  // Return to IDLE after error
END_IF
```

---

## 10. Connection Setup Checklist

1. **TwinCAT Route**: Add ADS route from PLC to WCS machine (both directions)
2. **AMS Net ID**: Configure in Equipment configJson `"adsNetId"`
3. **Port**: Default 851 (TwinCAT runtime)
4. **Firewall**: Allow TCP port 48898 (ADS) and 851
5. **Symbol Path**: Ensure GVL name matches `"symbolPath"` in configJson
6. **Time Sync**: Both machines should use NTP (±500ms tolerance)
