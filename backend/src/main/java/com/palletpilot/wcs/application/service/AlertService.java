package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Alert;
import com.palletpilot.wcs.infrastructure.persistence.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {

    private final AlertRepository alertRepository;
    private final WebSocketBroadcastService wsBroadcast;

    public void raiseAlert(UUID equipmentId, String message, String severity) {
        Alert alert = Alert.builder()
                .equipmentId(equipmentId)
                .message(message)
                .severity(severity)
                .build();

        Alert savedAlert = alertRepository.save(alert);
        log.warn("ALERT [{}]: {}", severity, message);

        // Push to Frontend via WebSocket
        wsBroadcast.sendAlert(savedAlert);
    }

    public Iterable<Alert> getAllAlerts() {
        return alertRepository.findAll();
    }

    public void deleteAlert(UUID id) {
        alertRepository.deleteById(id);
    }

    public void deleteAllAlerts() {
        alertRepository.deleteAll();
    }
}
