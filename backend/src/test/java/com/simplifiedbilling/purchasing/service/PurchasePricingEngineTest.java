package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasePricingEngineTest {

    private final PurchasePricingEngine engine = new PurchasePricingEngine();

    @Test
    void calculatesTaxInclusivePurchaseLines() {
        var result = engine.calculate(List.of(product("118.00", "18.00", "1")), true);

        assertThat(result.pricesIncludeTax()).isTrue();
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.lineNumber()).isEqualTo(1);
            assertThat(line.taxableAmount()).isEqualByComparingTo("100.00");
            assertThat(line.taxAmount()).isEqualByComparingTo("18.00");
            assertThat(line.lineTotal()).isEqualByComparingTo("118.00");
        });
        assertThat(result.subtotalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.taxAmount()).isEqualByComparingTo("18.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("118.00");
    }

    @Test
    void calculatesTaxExclusivePurchaseLinesAndRoundsMoney() {
        var result = engine.calculate(List.of(
                product("100.00", "18.00", "1"),
                product("12.35", "5.00", "2.500")), false);

        assertThat(result.pricesIncludeTax()).isFalse();
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(1).taxableAmount()).isEqualByComparingTo("30.88");
        assertThat(result.lines().get(1).taxAmount()).isEqualByComparingTo("1.54");
        assertThat(result.lines().get(1).lineTotal()).isEqualByComparingTo("32.42");
        assertThat(result.totalAmount()).isEqualByComparingTo("150.42");
    }

    private PurchaseProductSnapshot product(String cost, String gst, String quantity) {
        return new PurchaseProductSnapshot(
                "product-" + cost, "Rice", ProductUnit.KILOGRAM,
                new BigDecimal(quantity), new BigDecimal(cost), new BigDecimal(gst));
    }
}
