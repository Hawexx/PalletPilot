# KUKA TCP Interface Control Document (ICD)

**PalletPilot WCS ↔ KUKA Robot Controller (TCP)**
**Version:** 1.0 | **Date:** 2026-03-01 | **Status:** Active

---

## 1. Overview

Line-based TCP protocol for communication between WCS and KUKA robot controllers. Compatible with:
- KUKAVARPROXY
- Custom KRL TCP Server (recommended)
- Any TCP server implementing this protocol

### Connection
| Parameter | Default | Config Location |
|-----------|---------|----------------|
| IP | 192.168.1.10 | Equipment.ipAddress |
| Port | 30002 | Equipment.port |
| Timeout (connect) | 3000ms | `hardware.kuka.connection-timeout` |
| Timeout (read) | 30000ms | EquipmentCapabilities.timeoutMs |

---

## 2. Message Format

Line-based protocol. Each message terminated by `\n` (newline).

### 2.1 WCS → KUKA (Command)
```
CMD:<commandId>:<commandType>:<parameters>\n
```

| Field | Type | Description |
|-------|------|-------------|
| `CMD` | Literal | Message prefix |
| `commandId` | UUID (8-char short) | Unique command identifier |
| `commandType` | String | `PICK`, `DROP`, `HOME` |
| `parameters` | String | Comma-separated key=value pairs |

**Examples:**
```
CMD:a1b2c3d4:PICK:location=RACK-A1-01,speed=50\n
CMD:e5f6g7h8:DROP:location=CONV-OUT-1,speed=30\n
CMD:i9j0k1l2:HOME:\n
```

### 2.2 KUKA → WCS (Acknowledgment)
```
ACK:<commandId>\n
```
Sent immediately when command is received and queued.

### 2.3 KUKA → WCS (Completion)
```
DONE:<commandId>\n
```
Sent when command execution completes successfully.

### 2.4 KUKA → WCS (Error)
```
ERR:<commandId>:<errorCode>:<errorText>\n
```

| Field | Type | Description |
|-------|------|-------------|
| `errorCode` | Integer | KUKA error code (see §4) |
| `errorText` | String | Human-readable error |

**Example:**
```
ERR:a1b2c3d4:100:Gripper failed to close\n
```

### 2.5 KUKA → WCS (Heartbeat)
```
HB:<status>:<battery>\n
```
Sent every 5 seconds. Status: `IDLE`, `BUSY`, `ERROR`, `ESTOP`.

---

## 3. Timing

| Phase | Timeout | Behavior on Timeout |
|-------|---------|-------------------|
| Connect | 3000ms | Fail, mark OFFLINE |
| ACK wait | 5000ms | Retry once if idempotent, else fail |
| DONE wait (PICK/DROP) | 30000ms | Fail, raise alert |
| DONE wait (HOME) | 20000ms | Fail, raise alert |

---

## 4. Error Codes

| Code | Name | Description |
|------|------|-------------|
| 0 | NONE | No error |
| 100 | GRIP_FAIL | Gripper failed to close/open |
| 101 | POSITION_INACCURATE | Position tolerance exceeded |
| 102 | JOINT_LIMIT | Joint limit reached |
| 103 | PAYLOAD_EXCEEDED | Payload weight limit exceeded |
| 104 | MOTION_TIMEOUT | Motion did not complete in time |
| 200 | ESTOP | Emergency stop active |
| 201 | SAFETY_ZONE | Robot entered safety zone boundary |
| 999 | UNKNOWN | Unclassified error |

---

## 5. KRL Server Reference

```krl
; KRL TCP Server Skeleton (runs on KUKA controller)
; Listens on port 30002, handles WCS commands

DEF WCS_Server()
    DECL EXT CHANNEL CH1
    DECL CHAR cmd[256]
    DECL CHAR response[256]

    COPEN(:CH1, 30002)       ; Open TCP server

    LOOP
        CREAD(CH1, cmd[])    ; Read incoming command

        ; Parse CMD:<id>:<type>:<params>
        ; ...

        ; Send ACK
        response[] = "ACK:" + commandId
        CWRITE(CH1, response[])

        ; Execute motion
        SWITCH commandType
            CASE "PICK"
                LIN pickPosition
                GripperClose()
            CASE "DROP"
                LIN dropPosition
                GripperOpen()
            CASE "HOME"
                PTP HOME
        ENDSWITCH

        ; Send DONE
        response[] = "DONE:" + commandId
        CWRITE(CH1, response[])
    ENDLOOP

    CCLOSE(CH1)
END
```
