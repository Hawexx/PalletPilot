package com.palletpilot.wcs.infrastructure.movu.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.model.enums.EquipmentType;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves webhook tokens to equipment IDs.
 * Token stored in Equipment.configJson: {"movuWebhookToken": "secret-here"}
 *
 * This solves the critical "equipment resolution" problem:
 * even when MovuMission.equipmentId is null (external missions),
 * we can determine WHICH aSTC instance sent the webhook via its token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovuWebhookTokenService {

    private final EquipmentRepository equipmentRepository;
    private final ObjectMapper objectMapper;

    /**
     * Cache: token → equipmentId for fast lookup. Rebuilt on startup and on demand.
     */
    private final ConcurrentHashMap<String, UUID> tokenToEquipmentMap = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadTokens() {
        rebuildTokenCache();
    }

    /**
     * Rebuild the token → equipmentId cache from DB.
     * Call this when equipment config changes.
     */
    public void rebuildTokenCache() {
        tokenToEquipmentMap.clear();
        int count = 0;

        for (Equipment eq : equipmentRepository.findAll()) {
            if (eq.getType() != EquipmentType.MOVU)
                continue;
            if (eq.getConfigJson() == null || eq.getConfigJson().isBlank())
                continue;

            try {
                JsonNode config = objectMapper.readTree(eq.getConfigJson());
                JsonNode tokenNode = config.get("movuWebhookToken");
                if (tokenNode != null && !tokenNode.asText().isBlank()) {
                    tokenToEquipmentMap.put(tokenNode.asText(), eq.getId());
                    count++;
                    log.debug("Loaded webhook token for equipment: {} ({})", eq.getName(), eq.getId());
                }
            } catch (Exception e) {
                log.warn("Failed to parse configJson for equipment {}: {}", eq.getName(), e.getMessage());
            }
        }

        log.info("Loaded {} Movu webhook token(s) for equipment resolution", count);
    }

    /**
     * Resolve a webhook token to the equipment UUID.
     *
     * @param token The X-Movu-Webhook-Token header value
     * @return The equipment UUID if token is valid, empty if not
     */
    public Optional<UUID> resolveEquipmentId(String token) {
        if (token == null || token.isBlank())
            return Optional.empty();
        return Optional.ofNullable(tokenToEquipmentMap.get(token));
    }

    /**
     * Check if any tokens are configured (for graceful degradation when no tokens
     * exist).
     */
    public boolean hasConfiguredTokens() {
        return !tokenToEquipmentMap.isEmpty();
    }
}
