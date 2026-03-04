package com.palletpilot.wcs.domain.model.valueobjects;

public record PackingItem(String sku, BoxDimension dim, double weightKg, int quantity) {
}
