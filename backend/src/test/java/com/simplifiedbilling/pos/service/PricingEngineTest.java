package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.pos.domain.DiscountType;
import com.simplifiedbilling.pos.domain.TaxMode;
import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.shared.config.PosProperties;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingEngineTest {

    @Test
    void calculatesInclusiveIntraStateTaxAndAllocatedDiscounts() {
        PricingEngine engine = new PricingEngine(new PosProperties(true, true));
        var request = quote(
                List.of(
                        item("rice", "2", DiscountType.PERCENTAGE, "10"),
                        item("oil", "1", DiscountType.NONE, "0")),
                DiscountType.FIXED,
                "5",
                TaxMode.INTRA_STATE);

        var result = engine.calculate(request, List.of(
                product("rice", ProductUnit.PIECE, "100", "5", "10"),
                product("oil", ProductUnit.LITRE, "150", "12", "4")));

        assertThat(result.lines()).hasSize(2);
        assertThat(result.subtotalAmount()).isEqualByComparingTo("350.00");
        assertThat(result.lineDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(result.billDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("325.00");
        assertThat(result.roundOffAmount()).isEqualByComparingTo("0.00");
        assertThat(result.cgstAmount()).isPositive();
        assertThat(result.sgstAmount()).isPositive();
        assertThat(result.igstAmount()).isZero();
        assertThat(result.lines())
                .extracting(line -> line.billDiscountAmount())
                .containsExactly(new BigDecimal("2.73"), new BigDecimal("2.27"));
    }

    @Test
    void calculatesExclusiveInterStateTaxWithoutRounding() {
        PricingEngine engine = new PricingEngine(new PosProperties(false, false));
        var request = quote(
                List.of(item("rice", "1.250", DiscountType.FIXED, "5")),
                DiscountType.PERCENTAGE,
                "10",
                TaxMode.INTER_STATE);

        var result = engine.calculate(request, List.of(
                product("rice", ProductUnit.KILOGRAM, "80", "18", "5")));

        assertThat(result.pricesIncludeGst()).isFalse();
        assertThat(result.taxMode()).isEqualTo(TaxMode.INTER_STATE);
        assertThat(result.subtotalAmount()).isEqualByComparingTo("100.00");
        assertThat(result.lineDiscountAmount()).isEqualByComparingTo("5.00");
        assertThat(result.billDiscountAmount()).isEqualByComparingTo("9.50");
        assertThat(result.taxableAmount()).isEqualByComparingTo("85.50");
        assertThat(result.igstAmount()).isEqualByComparingTo("15.39");
        assertThat(result.cgstAmount()).isZero();
        assertThat(result.sgstAmount()).isZero();
        assertThat(result.totalAmount()).isEqualByComparingTo("100.89");
    }

    @Test
    void calculatesSimpleSaleWithoutAnyGstWhenCustomerGstinIsMissing() {
        PricingEngine engine = new PricingEngine(new PosProperties(false, false));
        var request = new PosRequests.QuoteRequest(
                List.of(item("rice", "1", DiscountType.NONE, "0")),
                DiscountType.NONE,
                BigDecimal.ZERO,
                TaxMode.INTER_STATE,
                null);

        var result = engine.calculate(request, List.of(
                product("rice", ProductUnit.PIECE, "100", "18", "5")));

        assertThat(result.customerGstin()).isNull();
        assertThat(result.taxableAmount()).isEqualByComparingTo("100.00");
        assertThat(result.cgstAmount()).isZero();
        assertThat(result.sgstAmount()).isZero();
        assertThat(result.igstAmount()).isZero();
        assertThat(result.lines().getFirst().cgstAmount()).isZero();
        assertThat(result.lines().getFirst().sgstAmount()).isZero();
        assertThat(result.lines().getFirst().igstAmount()).isZero();
        assertThat(result.totalAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void validatesCartShapeQuantityStockAndProductOrder() {
        PricingEngine engine = new PricingEngine(new PosProperties(true, true));
        SaleProductSnapshot piece = product("rice", ProductUnit.PIECE, "10", "0", "1");

        assertError(() -> engine.calculate(null, List.of()), "EMPTY_CART");
        assertError(() -> engine.calculate(
                quote(List.of(item("rice", "1", DiscountType.NONE, "0")), DiscountType.NONE, "0", TaxMode.INTRA_STATE),
                List.of()), "INVALID_CART");
        assertError(() -> engine.calculate(
                quote(List.of(item("other", "1", DiscountType.NONE, "0")), DiscountType.NONE, "0", TaxMode.INTRA_STATE),
                List.of(piece)), "INVALID_CART");
        assertError(() -> engine.calculate(
                quote(List.of(item("rice", "2", DiscountType.NONE, "0")), DiscountType.NONE, "0", TaxMode.INTRA_STATE),
                List.of(piece)), "INSUFFICIENT_STOCK");
        assertError(() -> engine.calculate(
                quote(List.of(item("rice", "0", DiscountType.NONE, "0")), DiscountType.NONE, "0", null),
                List.of(piece)), "INVALID_QUANTITY");
        assertError(() -> engine.calculate(
                quote(List.of(item("rice", "0.500", DiscountType.NONE, "0")), DiscountType.NONE, "0", TaxMode.INTRA_STATE),
                List.of(piece)), "FRACTIONAL_QUANTITY_NOT_ALLOWED");
        assertError(() -> engine.calculate(
                quote(List.of(item("rice", "0.0001", DiscountType.NONE, "0")), DiscountType.NONE, "0", TaxMode.INTRA_STATE),
                List.of(piece)), "INVALID_QUANTITY_PRECISION");
    }

    @Test
    void validatesPercentageFixedAndNegativeDiscounts() {
        PricingEngine engine = new PricingEngine(new PosProperties(true, false));
        SaleProductSnapshot product = product("rice", ProductUnit.PIECE, "10", "5", "2");

        assertDiscountError(engine, product, DiscountType.PERCENTAGE, "101", DiscountType.NONE, "0");
        assertDiscountError(engine, product, DiscountType.FIXED, "11", DiscountType.NONE, "0");
        assertDiscountError(engine, product, DiscountType.FIXED, "-1", DiscountType.NONE, "0");
        assertDiscountError(engine, product, DiscountType.NONE, "0", DiscountType.PERCENTAGE, "101");
        assertDiscountError(engine, product, DiscountType.NONE, "0", DiscountType.FIXED, "11");

        var free = engine.calculate(
                quote(List.of(item("rice", "1", DiscountType.NONE, null)), DiscountType.FIXED, "10", TaxMode.INTRA_STATE),
                List.of(product));
        assertThat(free.totalAmount()).isZero();
        assertThat(free.taxableAmount()).isZero();
    }

    private void assertDiscountError(
            PricingEngine engine,
            SaleProductSnapshot product,
            DiscountType lineType,
            String lineValue,
            DiscountType billType,
            String billValue) {
        assertError(() -> engine.calculate(
                quote(List.of(item("rice", "1", lineType, lineValue)), billType, billValue, TaxMode.INTRA_STATE),
                List.of(product)), "INVALID_DISCOUNT");
    }

    private PosRequests.QuoteRequest quote(
            List<PosRequests.CartItemRequest> items,
            DiscountType billType,
            String billValue,
            TaxMode taxMode) {
        return new PosRequests.QuoteRequest(
                items, billType, decimal(billValue), taxMode, "27ABCDE1234F1Z5");
    }

    private PosRequests.CartItemRequest item(
            String id,
            String quantity,
            DiscountType type,
            String value) {
        return new PosRequests.CartItemRequest(id, decimal(quantity), type, decimal(value));
    }

    private SaleProductSnapshot product(
            String id,
            ProductUnit unit,
            String price,
            String gst,
            String stock) {
        return new SaleProductSnapshot(
                id, "Rice", "Rice", "1234", unit, decimal(gst), decimal("6"),
                decimal(price), decimal(stock));
    }

    private BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable, String code) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(code));
    }
}
