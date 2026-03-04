package com.palletpilot.wcs.infrastructure.movu.config;

import com.palletpilot.wcs.application.service.AlertService;
import com.palletpilot.wcs.application.service.SystemStateService;
import com.palletpilot.wcs.domain.model.enums.SystemState;
import com.palletpilot.wcs.infrastructure.movu.exception.MovuExceptions.MovuTransientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Configures production-grade HTTP client, circuit breaker, and retry
 * for Movu aSTC communication.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MovuClientConfig {

        private final MovuHttpProperties props;

        /**
         * Pooled HTTP client using Java 21 HttpClient with virtual threads.
         * Connection pooling is managed internally by HttpClient.
         */
        @Bean
        public RestClient movuRestClient() {
                HttpClient httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                                .executor(Executors.newVirtualThreadPerTaskExecutor())
                                .build();

                JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
                requestFactory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));

                return RestClient.builder()
                                .requestFactory(requestFactory)
                                .defaultHeader("Accept", "application/json")
                                .defaultHeader("Content-Type", "application/json")
                                .build();
        }

        /**
         * Resilience4j Circuit Breaker for Movu aSTC calls.
         * Opens when failure rate exceeds threshold, preventing retry storms.
         */
        @Bean
        public CircuitBreaker movuCircuitBreaker(SystemStateService systemStateService,
                        AlertService alertService) {
                CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                                .failureRateThreshold(props.getBreakerFailureRateThreshold())
                                .slowCallDurationThreshold(Duration.ofMillis(props.getBreakerSlowCallDurationMs()))
                                .slowCallRateThreshold(props.getBreakerSlowCallRateThreshold())
                                .slidingWindowSize(props.getBreakerSlidingWindowSize())
                                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                                .waitDurationInOpenState(Duration.ofSeconds(props.getBreakerOpenStateDurationSeconds()))
                                .permittedNumberOfCallsInHalfOpenState(props.getBreakerPermittedCallsInHalfOpen())
                                .recordExceptions(MovuTransientException.class, TimeoutException.class)
                                .build();

                CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
                CircuitBreaker breaker = registry.circuitBreaker("movu-astc");

                // Event listeners for state transitions
                breaker.getEventPublisher()
                                .onStateTransition(event -> {
                                        log.warn("MOVU Circuit Breaker: {} → {}",
                                                        event.getStateTransition().getFromState(),
                                                        event.getStateTransition().getToState());

                                        if (event.getStateTransition().getToState() == CircuitBreaker.State.OPEN) {
                                                log.error("MOVU CIRCUIT BREAKER OPEN — aSTC unreachable, entering DEGRADED mode");
                                                try {
                                                        systemStateService.transitionTo(SystemState.DEGRADED,
                                                                        "CIRCUIT_BREAKER",
                                                                        "Movu aSTC circuit breaker opened: excessive failures");
                                                } catch (Exception e) {
                                                        log.warn("Could not transition to DEGRADED: {}",
                                                                        e.getMessage());
                                                }
                                                alertService.raiseAlert(null,
                                                                "Movu aSTC circuit breaker OPEN — system degraded. Check aSTC connectivity.",
                                                                "CRITICAL");
                                        }

                                        if (event.getStateTransition().getToState() == CircuitBreaker.State.CLOSED) {
                                                log.info("MOVU CIRCUIT BREAKER CLOSED — aSTC connectivity restored");
                                        }
                                });

                return breaker;
        }

        /**
         * Resilience4j Retry for idempotent Movu operations (GET, PUT).
         * Uses exponential backoff with jitter to avoid retry storms.
         */
        @Bean
        public Retry movuRetry() {
                RetryConfig config = RetryConfig.custom()
                                .maxAttempts(props.getRetryMaxAttempts())
                                .retryExceptions(MovuTransientException.class, TimeoutException.class)
                                .intervalFunction(attempt -> {
                                        // Exponential backoff: 250ms, 500ms, 1000ms... capped at max
                                        long backoff = Math.min(
                                                        props.getRetryInitialBackoffMs()
                                                                        * (long) Math.pow(2, attempt - 1),
                                                        props.getRetryMaxBackoffMs());
                                        // Add jitter
                                        long jitter = (long) (Math.random() * props.getRetryJitterMs());
                                        return backoff + jitter;
                                })
                                .build();

                RetryRegistry registry = RetryRegistry.of(config);
                Retry retry = registry.retry("movu-idempotent");

                // Log retries
                retry.getEventPublisher()
                                .onRetry(event -> log.warn("MOVU Retry attempt #{} for operation, waiting {}ms",
                                                event.getNumberOfRetryAttempts(), event.getWaitInterval().toMillis()));

                return retry;
        }
}
