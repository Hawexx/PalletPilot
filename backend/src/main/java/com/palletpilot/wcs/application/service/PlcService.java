package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.Equipment;
import com.palletpilot.wcs.infrastructure.persistence.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlcService {

    private final EquipmentService equipmentService;
    private final EquipmentRepository equipmentRepository;

    public void pickKuka(UUID equipmentId, String location) {
        String cmd = String.format("PICK:KUKA:%s:%s", getEquipmentName(equipmentId), location);
        equipmentService.executeCommand(equipmentId, cmd);
    }

    public void dropKuka(UUID equipmentId, String location) {
        String cmd = String.format("DROP:KUKA:%s:%s", getEquipmentName(equipmentId), location);
        equipmentService.executeCommand(equipmentId, cmd);
    }

    public void startConveyor(UUID equipmentId, int speed) {
        String cmd = String.format("START:CONVEYOR:%s:SPEED=%d", getEquipmentName(equipmentId), speed);
        equipmentService.executeCommand(equipmentId, cmd);
    }

    public void triggerWrapper(UUID equipmentId, String palletId) {
        String cmd = String.format("WRAP:WRAPPER:%s:PALLET=%s", getEquipmentName(equipmentId), palletId);
        equipmentService.executeCommand(equipmentId, cmd);
    }

    private String getEquipmentName(UUID id) {
        return equipmentRepository.findById(id)
                .map(Equipment::getName)
                .orElse("UNKNOWN");
    }
}
