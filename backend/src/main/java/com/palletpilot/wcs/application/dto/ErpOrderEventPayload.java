package com.palletpilot.wcs.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Incoming ERP order event payload.
 * Supports ORDER_CREATE, ORDER_UPDATE, ORDER_CANCEL.
 */
public record ErpOrderEventPayload(
        /** ORDER_CREATE | ORDER_UPDATE | ORDER_CANCEL */
        @NotBlank String eventType,

        /** Unique message ID from ERP (or null → WCS generates deterministic key) */
        String messageId,

        /** ISO-8601 event timestamp */
        String eventTime,

        /** ERP order identifier (SAP VBELN etc.) — dedupe key */
        @NotBlank String erpOrderId,

        /** Source system: SAP, ORACLE, CUSTOM */
        String erpSystem,

        /** Priority 1-10 */
        Integer priority,

        /** Customer code */
        String customerCode,

        /** Delivery date ISO-8601 */
        String deliveryDate,

        /** Warehouse code for multi-warehouse routing */
        String warehouseCode,

        /** Order lines — required for CREATE, optional for UPDATE/CANCEL */
        @Valid List<ErpOrderLineDto> lines) {
    public record ErpOrderLineDto(
            @NotBlank String sku,
            String description,
            @NotNull Integer quantity,
            Integer widthMm, // may be null → enrichment needed
            Integer lengthMm,
            Integer heightMm,
            Double weightKg,
            String lotNumber,
            String batchNumber,
            String expiryDate // ISO-8601
    ) {
    }
}
