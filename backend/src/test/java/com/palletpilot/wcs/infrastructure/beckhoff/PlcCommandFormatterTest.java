package com.palletpilot.wcs.infrastructure.beckhoff;

import com.palletpilot.wcs.domain.model.enums.CommandType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for PlcCommandFormatter.
 * Verifies:
 * - Canonical command string formatting
 * - Parameter encoding with stable delimiters
 * - STRING(255) truncation
 * - Sanitization (uppercase, no spaces/semicolons)
 */
class PlcCommandFormatterTest {

    private final PlcCommandFormatter formatter = new PlcCommandFormatter();

    @Test
    void simpleCommandWithoutParams() {
        String result = formatter.format(CommandType.KUKA_PICK, null);
        assertThat(result).isEqualTo("PICK");
    }

    @Test
    void simpleCommandWithEmptyParams() {
        String result = formatter.format(CommandType.CONVEYOR_STOP, Map.of());
        assertThat(result).isEqualTo("STOP");
    }

    @Test
    void commandWithSingleParam() {
        String result = formatter.format(CommandType.CONVEYOR_SET_SPEED, Map.of("speed", "1500"));
        assertThat(result).isEqualTo("SET_SPEED:SPEED=1500");
    }

    @Test
    void commandWithMultipleParams() {
        // Use LinkedHashMap for deterministic order
        Map<String, String> params = new LinkedHashMap<>();
        params.put("profile", "HEAVY");
        params.put("pallet", "PAL-001");

        String result = formatter.format(CommandType.WRAP_START, params);
        assertThat(result).isEqualTo("WRAP:PROFILE=HEAVY;PALLET=PAL-001");
    }

    @Test
    void sanitizesSpacesAndSpecialChars() {
        String result = formatter.format(CommandType.WRAP_SET_PROFILE, Map.of("profile name", "heavy duty;type"));
        assertThat(result).isEqualTo("SET_PROFILE:PROFILE_NAME=HEAVY_DUTY_TYPE");
    }

    @Test
    void truncatesTo255Chars() {
        // Create a param value that will make the total string > 255
        String longValue = "A".repeat(300);
        String result = formatter.format(CommandType.PLC_WRITE, Map.of("data", longValue));
        assertThat(result.length()).isLessThanOrEqualTo(255);
    }

    @ParameterizedTest
    @EnumSource(CommandType.class)
    void allCommandTypesProduceNonEmptyResult(CommandType type) {
        String result = formatter.format(type, null);
        assertThat(result).isNotBlank();
    }

    @Test
    void kukaCommands() {
        assertThat(formatter.format(CommandType.KUKA_PICK, null)).isEqualTo("PICK");
        assertThat(formatter.format(CommandType.KUKA_DROP, null)).isEqualTo("DROP");
        assertThat(formatter.format(CommandType.KUKA_HOME, null)).isEqualTo("HOME");
    }

    @Test
    void conveyorCommands() {
        assertThat(formatter.format(CommandType.CONVEYOR_START, null)).isEqualTo("START");
        assertThat(formatter.format(CommandType.CONVEYOR_STOP, null)).isEqualTo("STOP");
        assertThat(formatter.format(CommandType.CONVEYOR_SET_SPEED, null)).isEqualTo("SET_SPEED");
    }

    @Test
    void wrapperCommands() {
        assertThat(formatter.format(CommandType.WRAP_START, null)).isEqualTo("WRAP");
        assertThat(formatter.format(CommandType.WRAP_SET_PROFILE, null)).isEqualTo("SET_PROFILE");
        assertThat(formatter.format(CommandType.WRAP_STOP, null)).isEqualTo("WRAP_STOP");
    }
}
