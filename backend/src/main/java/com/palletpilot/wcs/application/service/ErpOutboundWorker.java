package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.ErpIntegrationLog;
import com.palletpilot.wcs.domain.model.ErpOutboxMessage;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpIntegrationLogRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.ErpOutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbound worker: sends queued ERP confirmations with retry/backoff.
 * Uses Outbox pattern for durable delivery.
 * DLQ after max attempts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ErpOutboundWorker {

    private final ErpOutboxMessageRepository outboxRepo;
    private final ErpIntegrationLogRepository logRepo;

    @Value("${erp.callback.retry-max-attempts:8}")
    private int maxAttempts;

    @Value("${erp.callback.retry-initial-backoff-ms:1000}")
    private int initialBackoffMs;

    @Value("${erp.callback.max-backoff-ms:60000}")
    private int maxBackoffMs;
    @Value("${erp.callback.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    private HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Scheduled(fixedDelayString = "${erp.outbound.worker.poll-ms:5000}")
    @Transactional
    public void pollAndSend() {
        List<ErpOutboxMessage> messages = outboxRepo.findReadyForSending(Instant.now());
        if (messages.isEmpty())
            return;

        log.info("[ERP-OUTBOUND] Sending {} outbox messages", messages.size());

        for (ErpOutboxMessage msg : messages) {
            sendMessage(msg);
        }
    }

    @Transactional
    public void sendMessage(ErpOutboxMessage msg) {
        MDC.put("correlationId", "outbox-" + msg.getId());
        MDC.put("erpOrderId", msg.getErpOrderId());
        try {
            msg.setAttemptCount(msg.getAttemptCount() + 1);
            log.info("[ERP-OUTBOUND] Sending: id={}, erpOrder={}, attempt={}/{}",
                    msg.getId(), msg.getErpOrderId(), msg.getAttemptCount(), maxAttempts);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(msg.getTargetUrl()))
                        .header("Content-Type", "application/json")
                        .header("X-WCS-Idempotency-Key", msg.getIdempotencyKey())
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(msg.getRequestJson()))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                msg.setHttpStatus(response.statusCode());
                msg.setResponseJson(response.body() != null && response.body().length() < 65536
                        ? response.body()
                        : "{\"truncated\":true}");

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    msg.setStatus("SENT");
                    log.info("[ERP-OUTBOUND] Successfully sent confirmation for order {}",
                            msg.getErpOrderId());
                } else {
                    throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
                }
            } catch (Exception e) {
                log.error("[ERP-OUTBOUND] Failed for order {}: {}", msg.getErpOrderId(), e.getMessage());
                msg.setLastError(e.getMessage());

                if (msg.getAttemptCount() >= maxAttempts) {
                    msg.setStatus("DLQ");
                    log.error("[ERP-OUTBOUND] Message {} moved to DLQ after {} attempts",
                            msg.getId(), msg.getAttemptCount());
                } else {
                    msg.setStatus("FAILED");
                    // Exponential backoff with jitter
                    long backoff = Math.min(maxBackoffMs,
                            initialBackoffMs * (long) Math.pow(2, msg.getAttemptCount() - 1));
                    long jitter = (long) (backoff * 0.1 * Math.random());
                    msg.setNextAttemptAt(Instant.now().plusMillis(backoff + jitter));
                }
            }

            outboxRepo.save(msg);

            // Log outbound event
            logRepo.save(ErpIntegrationLog.builder()
                    .direction("OUTBOUND")
                    .eventType(msg.getMessageType())
                    .erpOrderId(msg.getErpOrderId())
                    .wcsOrderId(msg.getWcsOrderId())
                    .status(msg.getStatus())
                    .requestJson(msg.getRequestJson())
                    .responseJson(msg.getResponseJson())
                    .httpStatus(msg.getHttpStatus())
                    .errorMessage(msg.getLastError())
                    .createdAt(Instant.now())
                    .processedAt(Instant.now())
                    .build());
        } finally {
            MDC.remove("correlationId");
            MDC.remove("erpOrderId");
        }
    }
}
