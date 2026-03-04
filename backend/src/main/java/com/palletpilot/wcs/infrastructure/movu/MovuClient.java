package com.palletpilot.wcs.infrastructure.movu;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.domain.port.out.HardwareClient;
import com.palletpilot.wcs.infrastructure.movu.config.MovuHttpProperties;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.*;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import com.palletpilot.wcs.infrastructure.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Movu aSTC REST client with production-grade reliability:
 * - Pooled HTTP client (configured externally)
 * - Circuit breaker (Resilience4j) — prevents retry storms
 * - Retry with exponential backoff + jitter for idempotent operations
 * - Typed exceptions for fine-grained error handling
 * - Correlation ID propagation (MDC → X-Correlation-Id header)
 * - Micrometer metrics (latency, retries, circuit state)
 */
@Component("movuClient")
@Slf4j
public class MovuClient implements HardwareClient {

    private final RestClient restClient;
    private final EquipmentRepository equipmentRepository;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final MovuHttpProperties props;
    private final MeterRegistry meterRegistry;

    // Cached base URL + token per equipment (avoids DB lookup per call)
    // TTL-based: auto-expires after 5 minutes to pick up config changes
    private final Cache<UUID, EquipmentContext> contextCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(50)
            .build();

    public MovuClient(RestClient movuRestClient,
            EquipmentRepository equipmentRepository,
            CircuitBreaker movuCircuitBreaker,
            Retry movuRetry,
            MovuHttpProperties props,
            MeterRegistry meterRegistry) {
        this.restClient = movuRestClient;
        this.equipmentRepository = equipmentRepository;
        this.circuitBreaker = movuCircuitBreaker;
        this.retry = movuRetry;
        this.props = props;
        this.meterRegistry = meterRegistry;
    }

    // ========================
    // PUBLIC API
    // ========================

    /**
     * Create a new mission on Movu aSTC.
     * NOT retried — POST createMission is non-idempotent (risk of double-create).
     */
    public String createMission(UUID equipmentId, String type, Map<String, Object> payload) {
        EquipmentContext ctx = resolveContext(equipmentId);

        return executeWithBreaker("missions.create", () -> {
            // No retry wrapping — createMission is non-idempotent
            Map response = doPost(ctx, "/api/v1.0/missions", payload);
            if (response != null && response.containsKey("id")) {
                String missionId = response.get("id").toString();
                log.info("[{}] Mission created: {} on equipment {}",
                        correlationId(), missionId, equipmentId);
                return missionId;
            }
            return null;
        });
    }

    /**
     * Send shuttle to specific lane/distance.
     * Non-idempotent (creates mission), no retry.
     */
    public void sendToLocation(UUID equipmentId, int lane, int distance) {
        log.info("[{}] Sending Movu {} to Lane {} Distance {}",
                correlationId(), equipmentId, lane, distance);

        EquipmentContext ctx = resolveContext(equipmentId);
        Map<String, Object> destination = Map.of(
                "level", 1, "lane", lane, "section", 1,
                "distance", distance, "approach", "Front");
        Map<String, Object> payload = Map.of(
                "vehicle", Map.of("id", ctx.movuId), "destination", destination);

        executeWithBreaker("missions.create", () -> {
            doPost(ctx, "/api/v1.0/missions", payload);
            return null;
        });
    }

    /**
     * Get mission status from aSTC.
     * RETRIED — GET is idempotent.
     */
    public String getMissionStatus(UUID equipmentId, String missionId) {
        if (equipmentId == null) {
            log.debug("Cannot get mission status: equipmentId is null (external mission)");
            return "UNKNOWN";
        }

        EquipmentContext ctx = resolveContext(equipmentId);

        try {
            return executeWithBreakerAndRetry("missions.get", () -> {
                Map response = doGet(ctx, "/api/v1.0/missions/" + missionId);
                if (response != null && response.containsKey("status")) {
                    return response.get("status").toString();
                }
                return "UNKNOWN";
            });
        } catch (MovuUnavailableException e) {
            log.warn("[{}] Circuit OPEN — cannot get status for mission {}",
                    correlationId(), missionId);
            return "UNKNOWN";
        } catch (Exception e) {
            log.warn("[{}] Failed to get status for mission {} after retries: {}",
                    correlationId(), missionId, e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * Get battery level from vehicle endpoint.
     * RETRIED — GET is idempotent.
     */
    public Integer getBatteryLevel(UUID equipmentId) {
        try {
            Map status = getVehicleStatus(equipmentId);
            if (status != null && status.containsKey("batteryLevel")) {
                return Integer.parseInt(status.get("batteryLevel").toString());
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to get battery for {}: {}",
                    correlationId(), equipmentId, e.getMessage());
        }
        return 0;
    }

    @Override
    public CompletableFuture<Boolean> sendCommand(UUID equipmentId, String command) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public boolean checkHealth(UUID equipmentId) {
        try {
            return getVehicleStatus(equipmentId) != null;
        } catch (MovuUnavailableException e) {
            return false;
        }
    }

    /**
     * Expose circuit breaker state for health checks and monitoring.
     */
    public CircuitBreaker.State getCircuitState() {
        return circuitBreaker.getState();
    }

    // ========================
    // PHASE D: LATE DECISION METHODS
    // ========================

    /**
     * Update destination for a mission (late decision).
     * PUT — idempotent, RETRIED.
     */
    public void updateDestination(UUID equipmentId, String missionId, Map<String, Object> location) {
        executeWithBreakerAndRetry("updateDestination", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.put()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/destination")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .body(location)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Update source for a mission (late decision).
     * PUT — idempotent, RETRIED.
     */
    public void updateSource(UUID equipmentId, String missionId, Map<String, Object> location) {
        executeWithBreakerAndRetry("updateSource", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.put()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/source")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .body(location)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Confirm shuttle entering destination.
     * POST — NOT retried (physical side effect, but idempotent-ish).
     * Uses circuit breaker only.
     */
    public void confirmEnterDestination(UUID equipmentId, String missionId) {
        executeWithBreaker("confirmEnterDestination", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.post()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/confirmenterdestination")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Confirm shuttle entering source.
     * POST — NOT retried (physical side effect).
     */
    public void confirmEnterSource(UUID equipmentId, String missionId) {
        executeWithBreaker("confirmEnterSource", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.post()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/confirmentersource")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Cancel a mission (before load is picked).
     * POST — NOT retried.
     */
    public void cancelMission(UUID equipmentId, String missionId) {
        executeWithBreaker("cancelMission", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.post()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/cancel")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Abort a mission (force stop, may require manual intervention).
     * POST — NOT retried.
     */
    public void abortMission(UUID equipmentId, String missionId) {
        executeWithBreaker("abortMission", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.post()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/abort")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Update mission priority.
     * PUT — idempotent, RETRIED.
     */
    public void updatePriority(UUID equipmentId, String missionId, int priority) {
        executeWithBreakerAndRetry("updatePriority", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.put()
                    .uri(ctx.baseUrl() + "/api/v1.0/missions/" + missionId + "/priority")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .body(Map.of("priority", priority))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    // ========================
    // PHASE E: DOCK/UNDOCK TASK METHODS
    // ========================

    /**
     * Dock a shuttle onto the lift.
     * POST — NOT retried (physical operation).
     */
    public void dockShuttle(UUID equipmentId, Map<String, Object> payload) {
        executeWithBreaker("dockShuttle", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.post()
                    .uri(ctx.baseUrl() + "/api/v1.0/tasks/dock")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Undock a shuttle from the lift.
     * POST — NOT retried (physical operation).
     */
    public void undockShuttle(UUID equipmentId, Map<String, Object> payload) {
        executeWithBreaker("undockShuttle", () -> {
            EquipmentContext ctx = resolveContext(equipmentId);
            restClient.post()
                    .uri(ctx.baseUrl() + "/api/v1.0/tasks/undock")
                    .header("Authorization", "Bearer " + ctx.token())
                    .header("X-Correlation-Id", correlationId())
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    // ========================
    // PRIVATE: EXECUTION WRAPPERS
    // ========================

    /**
     * Execute with circuit breaker only (for non-idempotent operations like POST
     * create).
     */
    private <T> T executeWithBreaker(String endpointTag, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";

        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, operation).get();
        } catch (CallNotPermittedException e) {
            result = "circuit_open";
            throw new MovuUnavailableException("Movu circuit breaker is OPEN — aSTC unreachable", e);
        } catch (MovuTransientException e) {
            result = "transient_error";
            throw e;
        } catch (MovuAuthException | MovuValidationException | MovuNotFoundException e) {
            result = "client_error";
            throw e;
        } catch (Exception e) {
            result = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder("movu.http.calls")
                    .tag("endpoint", endpointTag)
                    .tag("result", result)
                    .register(meterRegistry));
        }
    }

    /**
     * Execute with circuit breaker + retry (for idempotent GET/PUT operations).
     */
    private <T> T executeWithBreakerAndRetry(String endpointTag, Supplier<T> operation) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";

        try {
            // Order: retry wraps circuit-breaker wraps operation
            // Retry re-invokes the circuit-breaker decorated call
            Supplier<T> withBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, operation);
            Supplier<T> withRetry = Retry.decorateSupplier(retry, withBreaker);
            return withRetry.get();
        } catch (CallNotPermittedException e) {
            result = "circuit_open";
            throw new MovuUnavailableException("Movu circuit breaker is OPEN", e);
        } catch (MovuTransientException e) {
            result = "exhausted_retries";
            Counter.builder("movu.http.retries.exhausted")
                    .tag("endpoint", endpointTag)
                    .register(meterRegistry).increment();
            throw e;
        } catch (Exception e) {
            result = "error";
            throw e;
        } finally {
            sample.stop(Timer.builder("movu.http.calls")
                    .tag("endpoint", endpointTag)
                    .tag("result", result)
                    .register(meterRegistry));
        }
    }

    // ========================
    // PRIVATE: HTTP OPERATIONS
    // ========================

    private Map getVehicleStatus(UUID equipmentId) {
        EquipmentContext ctx = resolveContext(equipmentId);
        return executeWithBreakerAndRetry("vehicles.get", () -> doGet(ctx, "/api/v1.0/vehicles/" + ctx.movuId));
    }

    @SuppressWarnings("unchecked")
    private Map doGet(EquipmentContext ctx, String uri) {
        log.debug("[{}] GET {}{}", correlationId(), ctx.baseUrl, uri);

        try {
            return restClient.get()
                    .uri(ctx.baseUrl + uri)
                    .header("Authorization", "Bearer " + ctx.token)
                    .header(CorrelationIdFilter.HEADER_NAME, correlationId())
                    .retrieve()
                    .onStatus(this::isClientError, (req, resp) -> handleClientError(resp.getStatusCode(), uri))
                    .body(Map.class);
        } catch (ResourceAccessException e) {
            throw new MovuTransientException("Timeout/connection error: GET " + uri, e);
        } catch (HttpServerErrorException e) {
            throw new MovuTransientException("Server error " + e.getStatusCode() + ": GET " + uri,
                    e.getStatusCode().value());
        }
    }

    @SuppressWarnings("unchecked")
    private Map doPost(EquipmentContext ctx, String uri, Map<String, Object> body) {
        log.debug("[{}] POST {}{}", correlationId(), ctx.baseUrl, uri);

        try {
            return restClient.post()
                    .uri(ctx.baseUrl + uri)
                    .header("Authorization", "Bearer " + ctx.token)
                    .header(CorrelationIdFilter.HEADER_NAME, correlationId())
                    .body(body)
                    .retrieve()
                    .onStatus(this::isClientError, (req, resp) -> handleClientError(resp.getStatusCode(), uri))
                    .body(Map.class);
        } catch (ResourceAccessException e) {
            throw new MovuTransientException("Timeout/connection error: POST " + uri, e);
        } catch (HttpServerErrorException e) {
            throw new MovuTransientException("Server error " + e.getStatusCode() + ": POST " + uri,
                    e.getStatusCode().value());
        }
    }

    // ========================
    // PRIVATE: ERROR HANDLING
    // ========================

    private boolean isClientError(HttpStatusCode status) {
        return status.is4xxClientError();
    }

    private void handleClientError(HttpStatusCode status, String uri) {
        int code = status.value();
        if (code == 401 || code == 403) {
            throw new MovuAuthException("Authentication failed for " + uri + " (HTTP " + code + ")");
        } else if (code == 404) {
            throw new MovuNotFoundException("Resource not found: " + uri);
        } else if (code == 429) {
            // Rate limited — treat as transient for retry
            throw new MovuTransientException("Rate limited (429): " + uri, code);
        } else {
            throw new MovuValidationException("Validation error (HTTP " + code + "): " + uri, "");
        }
    }

    // ========================
    // PRIVATE: CONTEXT
    // ========================

    private EquipmentContext resolveContext(UUID equipmentId) {
        return contextCache.get(equipmentId, id -> {
            Equipment eq = equipmentRepository.findById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Equipment not found: " + id));
            String baseUrl = "http://" + eq.getIpAddress() + ":" +
                    (eq.getPort() != null ? eq.getPort() : 80);
            String token = eq.getApiKey();
            String movuId = eq.getName().replaceAll("\\D+", ""); // Extract digits from name
            return new EquipmentContext(baseUrl, token, movuId);
        });
    }

    /**
     * Invalidate cached context (e.g., when equipment config changes).
     */
    public void invalidateContext(UUID equipmentId) {
        contextCache.invalidate(equipmentId);
    }

    private String correlationId() {
        String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
        return cid != null ? cid : "no-cid";
    }

    private record EquipmentContext(String baseUrl, String token, String movuId) {
    }
}
