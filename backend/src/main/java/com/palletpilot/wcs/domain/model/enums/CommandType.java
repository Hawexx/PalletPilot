package com.palletpilot.wcs.domain.model.enums;

/**
 * Unified command types across all equipment protocols.
 * Used in CommandEnvelope and CommandLog for type-safe command classification.
 */
public enum CommandType {

    // --- KUKA Robot ---
    KUKA_PICK,
    KUKA_DROP,
    KUKA_HOME,

    // --- Conveyor ---
    CONVEYOR_START,
    CONVEYOR_STOP,
    CONVEYOR_SET_SPEED,

    // --- Wrapper (Cyklop) ---
    WRAP_START,
    WRAP_SET_PROFILE,
    WRAP_STOP,

    // --- PLC Generic ---
    PLC_READ,
    PLC_WRITE,
    PLC_GENERIC,

    // --- Movu AS/RS ---
    MOVU_MISSION_CREATE,
    MOVU_MISSION_CANCEL,

    // --- Generic ---
    EQUIPMENT_RESET,
    EQUIPMENT_TEST;

    /**
     * Whether this command type is safe to retry automatically.
     * Non-idempotent commands (PICK, DROP, WRAP) must NOT be auto-retried
     * to prevent physical double-execution.
     */
    public boolean isIdempotent() {
        return switch (this) {
            case PLC_READ, CONVEYOR_STOP, WRAP_STOP, EQUIPMENT_TEST, KUKA_HOME -> true;
            default -> false;
        };
    }

    /**
     * Default timeout in milliseconds for this command type.
     * Can be overridden per-equipment via EquipmentCapabilities.timeoutMs.
     */
    public int getDefaultTimeoutMs() {
        return switch (this) {
            case KUKA_PICK, KUKA_DROP -> 30_000;
            case KUKA_HOME -> 20_000;
            case CONVEYOR_START, CONVEYOR_STOP, CONVEYOR_SET_SPEED -> 10_000;
            case WRAP_START -> 60_000;
            case WRAP_SET_PROFILE, WRAP_STOP -> 10_000;
            case MOVU_MISSION_CREATE -> 30_000;
            case MOVU_MISSION_CANCEL -> 10_000;
            default -> 5_000;
        };
    }
}
