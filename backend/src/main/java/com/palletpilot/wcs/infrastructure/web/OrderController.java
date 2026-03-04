package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.OrderService;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.OrderItem;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Order Management")
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/orders")
    @Operation(summary = "List All Orders", description = "Returns all orders with their items")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get Order by ID")
    public ResponseEntity<Order> getOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PostMapping("/orders")
    @Operation(summary = "Create Order", description = "Creates a new order with items and sets initial CREATED status")
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @PutMapping("/orders/{id}")
    @Operation(summary = "Update Order", description = "Updates priority, status, or items of an existing order")
    public ResponseEntity<Order> updateOrder(@PathVariable UUID id, @Valid @RequestBody OrderUpdateRequest request) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    @DeleteMapping("/orders/{id}")
    @Operation(summary = "Delete Order")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders/{id}/items")
    @Operation(summary = "Get Order Items", description = "Returns all items for a specific order")
    public ResponseEntity<List<OrderItem>> getOrderItems(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id).getItems());
    }

    @PostMapping("/wms/ingest")
    @Operation(summary = "Ingest WMS Order", description = "Creates an order from WMS system with deduplication")
    public ResponseEntity<Order> ingestWmsOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ResponseEntity.ok(orderService.ingestWmsOrder(request));
    }

    // DTOs
    public record OrderItemDto(
            String sku,
            Integer widthMm,
            Integer lengthMm,
            Integer heightMm,
            Double weightKg,
            Integer quantity) {
    }

    public record OrderCreateRequest(
            @NotNull(message = "WMS Order ID is required") String wmsOrderId,
            @NotNull(message = "Priority is required") Integer priority,
            @NotEmpty(message = "Items cannot be empty") List<OrderItemDto> items) {
    }

    public record OrderUpdateRequest(
            Integer priority,
            ProcessState status,
            List<OrderItemDto> items) {
    }
}
