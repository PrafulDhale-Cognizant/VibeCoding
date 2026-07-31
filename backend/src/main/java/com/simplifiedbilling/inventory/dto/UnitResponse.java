package com.simplifiedbilling.inventory.dto;

import com.simplifiedbilling.inventory.domain.ProductUnit;

public record UnitResponse(
        ProductUnit code,
        String displayName,
        String symbol,
        boolean decimalAllowed) {
}
