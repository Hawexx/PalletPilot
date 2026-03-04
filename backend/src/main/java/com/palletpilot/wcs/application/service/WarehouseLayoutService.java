package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.model.WarehouseLayout;
import com.palletpilot.wcs.infrastructure.persistence.repository.WarehouseLayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import com.palletpilot.wcs.infrastructure.license.LicenseGuard;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseLayoutService {

    private final WarehouseLayoutRepository repository;
    private static final String DEFAULT_LAYOUT_ID = "DEFAULT";

    @Transactional(readOnly = true)
    public Optional<WarehouseLayout> getLayout() {
        LicenseGuard.assertRuntimeValidity();
        return repository.findById(DEFAULT_LAYOUT_ID);
    }

    @Transactional
    public WarehouseLayout saveLayout(String layoutJson) {
        log.info("Saving warehouse layout configuration");
        WarehouseLayout layout = repository.findById(DEFAULT_LAYOUT_ID)
                .orElse(WarehouseLayout.builder()
                        .id(DEFAULT_LAYOUT_ID)
                        .build());

        layout.setLayoutJson(layoutJson);
        return repository.save(layout);
    }
}
