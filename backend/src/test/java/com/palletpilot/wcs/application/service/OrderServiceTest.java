package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.domain.exception.DuplicateResourceException;
import com.palletpilot.wcs.domain.exception.ResourceNotFoundException;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.domain.service.PalletBuildingService;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import com.palletpilot.wcs.infrastructure.persistence.repository.PalletRepository;
import com.palletpilot.wcs.infrastructure.web.OrderController.OrderCreateRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService — CRUD + WMS ingest + auto-planning.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PalletBuildingService palletBuildingService;
    @Mock
    private PalletRepository palletRepository;
    @Mock
    private AuditService auditService;

    @BeforeAll
    static void initLicense() {
        com.palletpilot.wcs.infrastructure.license.LicenseGuard.earlyInstall();
    }

    @InjectMocks
    private OrderService orderService;

    private UUID testOrderId;

    @BeforeEach
    void setUp() {
        testOrderId = UUID.randomUUID();
    }

    private Order createTestOrder(String wmsOrderId) {
        Order order = Order.builder()
                .wmsOrderId(wmsOrderId)
                .priority(5)
                .status(ProcessState.CREATED)
                .items(new ArrayList<>())
                .build();
        try {
            var idField = Order.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(order, testOrderId);
        } catch (Exception ignored) {
        }
        return order;
    }

    @Test
    @DisplayName("getAllOrders: returns all orders from repository")
    void testGetAllOrders() {
        Order o1 = createTestOrder("WMS-001");
        Order o2 = createTestOrder("WMS-002");
        when(orderRepository.findAll()).thenReturn(List.of(o1, o2));

        List<Order> result = orderService.getAllOrders();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getOrder: existing ID → returns order")
    void testGetOrder_found() {
        Order order = createTestOrder("WMS-001");
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.of(order));

        Order result = orderService.getOrder(testOrderId);

        assertThat(result.getWmsOrderId()).isEqualTo("WMS-001");
    }

    @Test
    @DisplayName("getOrder: missing ID → throws RuntimeException")
    void testGetOrder_notFound() {
        when(orderRepository.findById(testOrderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(testOrderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("createOrder: new order → saved with CREATED status")
    void testCreateOrder_success() {
        OrderCreateRequest request = new OrderCreateRequest("WMS-001", 5, List.of());
        when(orderRepository.existsByWmsOrderId("WMS-001")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            try {
                var idField = Order.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(o, testOrderId);
            } catch (Exception ignored) {
            }
            return o;
        });

        Order result = orderService.createOrder(request);

        assertThat(result.getStatus()).isEqualTo(ProcessState.CREATED);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: duplicate WMS ID → throws RuntimeException")
    void testCreateOrder_duplicate() {
        OrderCreateRequest request = new OrderCreateRequest("WMS-001", 5, List.of());
        when(orderRepository.existsByWmsOrderId("WMS-001")).thenReturn(true);

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("deleteOrder: existing ID → deletes pallets and order")
    void testDeleteOrder_success() {
        when(orderRepository.existsById(testOrderId)).thenReturn(true);

        orderService.deleteOrder(testOrderId);

        verify(palletRepository).deleteByOrderId(testOrderId);
        verify(orderRepository).deleteById(testOrderId);
    }

    @Test
    @DisplayName("deleteOrder: missing ID → throws RuntimeException")
    void testDeleteOrder_notFound() {
        when(orderRepository.existsById(testOrderId)).thenReturn(false);

        assertThatThrownBy(() -> orderService.deleteOrder(testOrderId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    @DisplayName("ingestWmsOrder: existing order → returns it (idempotent)")
    void testIngestWmsOrder_existing() {
        Order existing = createTestOrder("WMS-001");
        when(orderRepository.existsByWmsOrderId("WMS-001")).thenReturn(true);
        when(orderRepository.findByWmsOrderId("WMS-001")).thenReturn(Optional.of(existing));

        OrderCreateRequest request = new OrderCreateRequest("WMS-001", 5, List.of());
        Order result = orderService.ingestWmsOrder(request);

        assertThat(result.getWmsOrderId()).isEqualTo("WMS-001");
        verify(orderRepository, never()).save(any());
    }
}
