package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.application.dto.WmsOrderRequest;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.OrderItem;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WmsIngestionService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order ingestOrder(WmsOrderRequest request) {
        log.info("Ingesting WMS Order: {}", request.wmsOrderId());

        if (orderRepository.existsByWmsOrderId(request.wmsOrderId())) {
            throw new com.palletpilot.wcs.domain.exception.DuplicateResourceException("Order", request.wmsOrderId());
        }

        Order order = Order.builder()
                .wmsOrderId(request.wmsOrderId())
                .status(ProcessState.CREATED) // Initial state
                .priority(request.priority())
                .build();

        // Map items
        List<OrderItem> items = request.items().stream().map(dto -> OrderItem.builder()
                .order(order)
                .sku(dto.sku())
                .widthMm(dto.widthMm())
                .lengthMm(dto.lengthMm())
                .heightMm(dto.heightMm())
                .weightKg(dto.weightKg())
                .quantity(dto.quantity())
                .build()).collect(Collectors.toList());

        order.setItems(items);

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} ingested successfully with {} items.", savedOrder.getId(), savedOrder.getItems().size());
        return savedOrder;
    }
}
