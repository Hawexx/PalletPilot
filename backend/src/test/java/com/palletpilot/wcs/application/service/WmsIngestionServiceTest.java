package com.palletpilot.wcs.application.service;

import com.palletpilot.wcs.application.dto.WmsOrderRequest;
import com.palletpilot.wcs.domain.exception.DuplicateResourceException;
import com.palletpilot.wcs.domain.model.Order;
import com.palletpilot.wcs.domain.model.enums.ProcessState;
import com.palletpilot.wcs.infrastructure.persistence.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WmsIngestionService}.
 * Validates WMS order ingestion with deduplication.
 */
@ExtendWith(MockitoExtension.class)
class WmsIngestionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private WmsIngestionService service;

    private WmsOrderRequest createRequest(String wmsOrderId) {
        return new WmsOrderRequest(
                wmsOrderId,
                5,
                List.of(
                        new WmsOrderRequest.WmsOrderItemDto("SKU-001", 200, 300, 150, 5.0, 2),
                        new WmsOrderRequest.WmsOrderItemDto("SKU-002", 400, 300, 200, 8.0, 1)));
    }

    @Test
    @DisplayName("ingestOrder — new order should be created with CREATED status")
    void testIngestOrder_success() {
        WmsOrderRequest request = createRequest("WMS-101");
        when(orderRepository.existsByWmsOrderId("WMS-101")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            try {
                var idField = Order.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(o, UUID.randomUUID());
            } catch (Exception ignored) {
            }
            return o;
        });

        Order result = service.ingestOrder(request);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(ProcessState.CREATED);
        assertThat(result.getWmsOrderId()).isEqualTo("WMS-101");
        assertThat(result.getItems()).hasSize(2);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    @DisplayName("ingestOrder — duplicate WMS ID should throw DuplicateResourceException")
    void testIngestOrder_duplicate() {
        WmsOrderRequest request = createRequest("WMS-101");
        when(orderRepository.existsByWmsOrderId("WMS-101")).thenReturn(true);

        assertThatThrownBy(() -> service.ingestOrder(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("ingestOrder — items should be correctly mapped with dimensions")
    void testIngestOrder_itemMapping() {
        WmsOrderRequest request = createRequest("WMS-102");
        when(orderRepository.existsByWmsOrderId("WMS-102")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            try {
                var idField = Order.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(o, UUID.randomUUID());
            } catch (Exception ignored) {
            }
            return o;
        });

        Order result = service.ingestOrder(request);

        assertThat(result.getItems().get(0).getSku()).isEqualTo("SKU-001");
        assertThat(result.getItems().get(0).getWidthMm()).isEqualTo(200);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getItems().get(1).getSku()).isEqualTo("SKU-002");
        assertThat(result.getItems().get(1).getWeightKg()).isEqualTo(8.0);
    }

    @Test
    @DisplayName("ingestOrder — priority should be set from request")
    void testIngestOrder_priority() {
        WmsOrderRequest request = new WmsOrderRequest("WMS-103", 10,
                List.of(new WmsOrderRequest.WmsOrderItemDto("SKU-001", 200, 300, 150, 5.0, 1)));
        when(orderRepository.existsByWmsOrderId("WMS-103")).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            try {
                var idField = Order.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(o, UUID.randomUUID());
            } catch (Exception ignored) {
            }
            return o;
        });

        Order result = service.ingestOrder(request);

        assertThat(result.getPriority()).isEqualTo(10);
    }
}
