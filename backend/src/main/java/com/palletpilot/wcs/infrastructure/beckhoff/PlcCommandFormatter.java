package com.palletpilot.wcs.infrastructure.beckhoff;

import com.palletpilot.wcs.domain.model.enums.CommandType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Formats PLC command strings per the TwinCAT ICD v1.0 spec.
 * <p>
 * Commands are single-field STRING(255) written to {symbolPath}.Command.Cmd.
 * Format: ACTION or ACTION:PARAM1=VAL1;PARAM2=VAL2
 * <p>
 * All strings are uppercase, no spaces, stable delimiters.
 */
@Component
@Slf4j
public class PlcCommandFormatter {

    private static final int MAX_CMD_LENGTH = 255;

    /**
     * Format a PLC command string for the given command type and parameters.
     *
     * @param type   the command type (e.g., KUKA_PICK, CONVEYOR_START)
     * @param params optional key-value parameters (e.g., SPEED=1500,
     *               PALLET=PAL-001)
     * @return formatted command string, truncated to 255 chars
     */
    public String format(CommandType type, Map<String, String> params) {
        String action = toPlcAction(type);

        if (params == null || params.isEmpty()) {
            return truncate(action);
        }

        StringBuilder sb = new StringBuilder(action);
        sb.append(':');
        boolean first = true;
        for (var entry : params.entrySet()) {
            if (!first)
                sb.append(';');
            sb.append(sanitize(entry.getKey()))
                    .append('=')
                    .append(sanitize(entry.getValue()));
            first = false;
        }

        return truncate(sb.toString());
    }

    /**
     * Map CommandType enum to TwinCAT PLC action string.
     */
    private String toPlcAction(CommandType type) {
        return switch (type) {
            // KUKA
            case KUKA_PICK -> "PICK";
            case KUKA_DROP -> "DROP";
            case KUKA_HOME -> "HOME";
            // Conveyor
            case CONVEYOR_START -> "START";
            case CONVEYOR_STOP -> "STOP";
            case CONVEYOR_SET_SPEED -> "SET_SPEED";
            // Wrapper
            case WRAP_START -> "WRAP";
            case WRAP_SET_PROFILE -> "SET_PROFILE";
            case WRAP_STOP -> "WRAP_STOP";
            // PLC Generic
            case PLC_READ -> "READ";
            case PLC_WRITE -> "WRITE";
            case PLC_GENERIC -> "GENERIC";
            // Movu (not PLC — should not reach here)
            case MOVU_MISSION_CREATE, MOVU_MISSION_CANCEL -> "MOVU_" + type.name();
            // Generic
            case EQUIPMENT_RESET -> "RESET";
            case EQUIPMENT_TEST -> "TEST";
        };
    }

    /**
     * Sanitize a value for safe PLC string: uppercase, no spaces, no semicolons.
     */
    private String sanitize(String value) {
        if (value == null)
            return "";
        return value.toUpperCase()
                .replace(" ", "_")
                .replace(";", "_")
                .replace(":", "_");
    }

    private String truncate(String cmd) {
        if (cmd.length() > MAX_CMD_LENGTH) {
            log.warn("[PLC-FMT] Command truncated from {} to {} chars: {}", cmd.length(), MAX_CMD_LENGTH, cmd);
            return cmd.substring(0, MAX_CMD_LENGTH);
        }
        return cmd;
    }
}
