package com.palletpilot.wcs.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record WmsOrderRequest(
        @NotNull(message = "WMS Order ID is required") String wmsOrderId,
        @NotNull(message = "Priority is required") Integer priority,
        @NotEmpty(message = "Order items cannot be empty") List<WmsOrderItemDto> items) {
    public record WmsOrderItemDto(
            @NotNull String sku,
            @NotNull Integer widthMm,
            @NotNull Integer lengthMm,
            @NotNull Integer heightMm,
            @NotNull Double weightKg,
            @NotNull Integer quantity) {
    }
}
