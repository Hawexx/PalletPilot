package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.exception.DuplicateResourceException;
import com.palletpilot.wcs.domain.exception.ResourceNotFoundException;

import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.OrderItem;
import com.palletpilot.wcs.domain.model.Pallet;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.model.valueobjects.BoxDimension;
import com.palletpilot.wcs.domain.model.valueobjects.PackingItem;
import com.palletpilot.wcs.domain.service.PalletBuildingService;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.web.OrderController.OrderCreateRequest;
import com.palletpilot.wcs.infrastructure.web.OrderController.OrderUpdateRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.palletpilot.wcs.infrastructure.license.LicenseGuard;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final PalletBuildingService palletBuildingService;
    private final PalletRepository palletRepository;
    private final AuditService auditService;

    @Getter
    @Setter
    private boolean autoPlanningEnabled = false;

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        LicenseGuard.assertRuntimeValidity();
        return orderRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        LicenseGuard.assertRuntimeValidity();
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    @Transactional
    public Order createOrder(OrderCreateRequest request) {
        if (orderRepository.existsByWmsOrderId(request.wmsOrderId())) {
            throw new DuplicateResourceException("Order", request.wmsOrderId());
        }

        Order order = Order.builder()
                .wmsOrderId(request.wmsOrderId())
                .priority(request.priority() != null ? request.priority() : 5)
                .status(ProcessState.CREATED)
                .items(new ArrayList<>())
                .build();

        if (request.items() != null && !request.items().isEmpty()) {
            List<OrderItem> items = request.items().stream().map(itemDto -> OrderItem.builder()
                    .order(order)
                    .sku(itemDto.sku())
                    .widthMm(itemDto.widthMm())
                    .lengthMm(itemDto.lengthMm())
                    .heightMm(itemDto.heightMm())
                    .weightKg(itemDto.weightKg())
                    .quantity(itemDto.quantity())
                    .build()).collect(Collectors.toList());
            order.setItems(items);
        }

        Order savedOrder = orderRepository.save(order);

        auditService.log("ORDER_CREATED", "Order", savedOrder.getId().toString(), null,
                String.format("{\"wmsOrderId\":\"%s\",\"itemCount\":%d}",
                        request.wmsOrderId(), savedOrder.getItems().size()));

        if (autoPlanningEnabled && !savedOrder.getItems().isEmpty()) {
            try {
                // Convert to PackingItems
                List<PackingItem> packingItems = savedOrder.getItems().stream()
                        .map(i -> new PackingItem(
                                i.getSku(),
                                new BoxDimension(i.getWidthMm(), i.getLengthMm(), i.getHeightMm()),
                                i.getWeightKg() != null ? i.getWeightKg() : 0.0,
                                i.getQuantity()))
                        .collect(Collectors.toList());

                // Use new multi-pallet planning
                List<Pallet> pallets = palletBuildingService.planPallets(savedOrder.getId(), packingItems);
                palletRepository.saveAll(pallets);
                log.info("Auto-planned {} pallet(s) for order: {}", pallets.size(), savedOrder.getId());

                savedOrder.setStatus(ProcessState.PLANNED);
                return orderRepository.save(savedOrder);
            } catch (Exception e) {
                log.error("Auto-planning failed for order: " + savedOrder.getId(), e);
                // Don't fail the order creation, just log
            }
        }

        return savedOrder;
    }

    @Transactional
    public Order updateOrder(UUID id, OrderUpdateRequest request) {
        Order order = getOrder(id);

        if (request.priority() != null) {
            order.setPriority(request.priority());
        }
        if (request.status() != null) {
            order.setStatus(request.status());
        }
        if (request.items() != null) {
            // Replace items
            order.getItems().clear();
            List<OrderItem> newItems = request.items().stream().map(dto -> OrderItem.builder()
                    .order(order)
                    .sku(dto.sku())
                    .widthMm(dto.widthMm())
                    .lengthMm(dto.lengthMm())
                    .heightMm(dto.heightMm())
                    .weightKg(dto.weightKg())
                    .quantity(dto.quantity())
                    .build()).collect(Collectors.toList());
            order.getItems().addAll(newItems);
        }
        return orderRepository.save(order);
    }

    @Transactional
    public void deleteOrder(UUID id) {
        if (!orderRepository.existsById(id)) {
            throw new ResourceNotFoundException("Order", id);
        }
        // Cascade delete pallet plans manually
        palletRepository.deleteByOrderId(id);

        orderRepository.deleteById(id);
    }

    @Transactional
    public Order ingestWmsOrder(OrderCreateRequest request) {
        if (orderRepository.existsByWmsOrderId(request.wmsOrderId())) {
            return orderRepository.findByWmsOrderId(request.wmsOrderId())
                    .orElseThrow(() -> new RuntimeException("Order found but could not be retrieved"));
        }
        return createOrder(request);
    }
}
