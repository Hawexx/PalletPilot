package com.palletpilot.wcs.infrastructure.web;

import com.palletpilot.wcs.application.service.OrderService;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.OrderItem;
import com.palletpilot.wcs.domain.exception.ResourceNotFoundException;
import com.palletpilot.wcs.infrastructure.web.OrderController.OrderCreateRequest;
import com.palletpilot.wcs.infrastructure.web.OrderController.OrderItemDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderController.
 * Validates delegation to OrderService, response status codes,
 * and correct handling of edge cases (empty list, not found).
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController controller;

    // ── GET /orders ──

    @Test
    @DisplayName("getAllOrders → returns 200 with list of orders")
    void getAllOrders_returnsOkWithList() {
        Order order = Order.builder().id(UUID.randomUUID()).wmsOrderId("WMS-001").build();
        when(orderService.getAllOrders()).thenReturn(List.of(order));

        ResponseEntity<List<Order>> response = controller.getAllOrders();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getWmsOrderId()).isEqualTo("WMS-001");
    }

    @Test
    @DisplayName("getAllOrders → returns 200 with empty list when no orders")
    void getAllOrders_emptyList() {
        when(orderService.getAllOrders()).thenReturn(Collections.emptyList());

        ResponseEntity<List<Order>> response = controller.getAllOrders();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    // ── GET /orders/{id} ──

    @Test
    @DisplayName("getOrder → returns 200 with matching order")
    void getOrder_returnsOk() {
        UUID id = UUID.randomUUID();
        Order order = Order.builder().id(id).wmsOrderId("WMS-002").build();
        when(orderService.getOrder(id)).thenReturn(order);

        ResponseEntity<Order> response = controller.getOrder(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(id);
    }

    @Test
    @DisplayName("getOrder → propagates ResourceNotFoundException for unknown ID")
    void getOrder_notFound_propagatesException() {
        UUID id = UUID.randomUUID();
        when(orderService.getOrder(id)).thenThrow(new ResourceNotFoundException("Order", id));

        assertThatThrownBy(() -> controller.getOrder(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── POST /orders ──

    @Test
    @DisplayName("createOrder → delegates to service and returns 200")
    void createOrder_delegatesToService() {
        OrderCreateRequest request = new OrderCreateRequest(
                "WMS-003", 1,
                List.of(new OrderItemDto("SKU-A", 400, 600, 300, 5.0, 10)));

        Order created = Order.builder().id(UUID.randomUUID()).wmsOrderId("WMS-003").build();
        when(orderService.createOrder(any())).thenReturn(created);

        ResponseEntity<Order> response = controller.createOrder(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getWmsOrderId()).isEqualTo("WMS-003");
        verify(orderService).createOrder(request);
    }

    // ── DELETE /orders/{id} ──

    @Test
    @DisplayName("deleteOrder → returns 204 No Content")
    void deleteOrder_returnsNoContent() {
        UUID id = UUID.randomUUID();

        ResponseEntity<Void> response = controller.deleteOrder(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(orderService).deleteOrder(id);
    }

    // ── GET /orders/{id}/items ──

    @Test
    @DisplayName("getOrderItems → returns items from the order")
    void getOrderItems_returnsItems() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.builder().sku("SKU-B").quantity(5).build();
        Order order = Order.builder().id(orderId).items(List.of(item)).build();
        when(orderService.getOrder(orderId)).thenReturn(order);

        ResponseEntity<List<OrderItem>> response = controller.getOrderItems(orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getSku()).isEqualTo("SKU-B");
    }
}
