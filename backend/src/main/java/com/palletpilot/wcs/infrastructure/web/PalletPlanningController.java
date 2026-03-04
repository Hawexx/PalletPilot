package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.OrderService;
import com.palletpilot.wcs.domain.model.Pallet;
import com.palletpilot.wcs.domain.model.valueobjects.BoxDimension;
import com.palletpilot.wcs.domain.model.valueobjects.PackingItem;
import com.palletpilot.wcs.domain.service.PalletBuildingService;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Pallet Planning")
@RequestMapping("/api/pallets")
@RequiredArgsConstructor
@Slf4j
public class PalletPlanningController {

    private final OrderService orderService;
    private final PalletBuildingService palletBuildingService;
    private final PalletRepository palletRepository;

    @PostMapping("/plan")
    @Operation(summary = "Generate Pallet Plan", description = "Creates optimized pallet layout from order items")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<List<Pallet>> generatePalletPlan(@RequestBody PlanRequest request) {
        log.info("Generating pallet plan for order: {}", request.orderId());

        List<PackingItem> itemsToPlan = request.items();

        // Fetch items from Order if not provided in request
        if (itemsToPlan == null || itemsToPlan.isEmpty()) {
            var order = orderService.getOrder(request.orderId());
            if (order.getItems() == null || order.getItems().isEmpty()) {
                throw new com.palletpilot.wcs.domain.exception.BusinessRuleViolationException("EMPTY_ORDER",
                        "Order has no items to plan.");
            }
            itemsToPlan = order.getItems().stream()
                    .map(i -> new PackingItem(
                            i.getSku(),
                            new BoxDimension(i.getWidthMm(), i.getLengthMm(), i.getHeightMm()),
                            i.getWeightKg() != null ? i.getWeightKg() : 0.0,
                            i.getQuantity()))
                    .toList();
        }

        // Cleanup existing plans for this order to prevent duplicates
        palletRepository.deleteByOrderId(request.orderId());

        // Generate all pallets (multi-pallet support)
        List<Pallet> pallets = palletBuildingService.planPallets(request.orderId(), itemsToPlan);

        // Save all pallets to PostgreSQL
        List<Pallet> savedPallets = palletRepository.saveAll(pallets);

        log.info("Generated {} pallets for order {}", savedPallets.size(), request.orderId());

        return ResponseEntity.ok(savedPallets);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Pallet by ID")
    public ResponseEntity<Pallet> getPallet(@PathVariable UUID id) {
        log.info("Getting pallet: {}", id);
        return palletRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "List All Pallets")
    public ResponseEntity<List<Pallet>> getAllPallets() {
        log.info("Getting all pallets");
        return ResponseEntity.ok(palletRepository.findAll());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Pallet")
    public ResponseEntity<Void> deletePallet(@PathVariable UUID id) {
        log.info("Deleting pallet: {}", id);
        if (palletRepository.existsById(id)) {
            palletRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}/state")
    @Operation(summary = "Update Pallet State")
    public ResponseEntity<Pallet> updatePalletState(
            @PathVariable UUID id,
            @RequestBody StateUpdateRequest request) {
        log.info("Updating pallet {} state to: {}", id, request.state());

        return palletRepository.findById(id)
                .map(pallet -> {
                    pallet.setProcessState(request.state());
                    return ResponseEntity.ok(palletRepository.save(pallet));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Request DTOs
    public record PlanRequest(
            UUID orderId,
            List<PackingItem> items) {
    }

    public record StateUpdateRequest(
            com.palletpilot.wcs.domain.model.enums.ProcessState state) {
    }
}
