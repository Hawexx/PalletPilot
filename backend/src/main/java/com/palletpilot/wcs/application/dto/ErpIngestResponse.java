package com.palletpilot.wcs.application.dto;

/**
 * Response DTO for ERP ingest endpoint.
 * Always returned quickly with status info.
 */
public record ErpIngestResponse(
        String messageId,
        String status, // RECEIVED, DUPLICATE, INVALID
        String erpOrderId,
        String wcsOrderId, // null if not yet processed
        String detail) {
    public static ErpIngestResponse received(String messageId, String erpOrderId) {
        return new ErpIngestResponse(messageId, "RECEIVED", erpOrderId, null, null);
    }

    public static ErpIngestResponse duplicate(String messageId, String erpOrderId, String wcsOrderId) {
        return new ErpIngestResponse(messageId, "DUPLICATE", erpOrderId, wcsOrderId,
                "Message already processed");
    }

    public static ErpIngestResponse invalid(String messageId, String detail) {
        return new ErpIngestResponse(messageId, "INVALID", null, null, detail);
    }
}
