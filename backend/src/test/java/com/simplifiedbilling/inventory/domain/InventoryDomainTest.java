package com.simplifiedbilling.inventory.domain;

import com.simplifiedbilling.inventory.dto.ProductCreateRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryDomainTest {

    private static final Instant NOW = Instant.parse("2026-07-31T10:00:00Z");

    @Test
    void categoryCanBeCreatedAndUpdated() {
        Category category = Category.create("Grocery", NOW);

        assertThat(category.getId()).isNotBlank();
        assertThat(category.getName()).isEqualTo("Grocery");
        assertThat(category.isActive()).isTrue();
        assertThat(category.getVersion()).isZero();
        assertThat(category.getCreatedAt()).isEqualTo(NOW);

        category.update("Daily Needs", false, NOW.plusSeconds(1));

        assertThat(category.getName()).isEqualTo("Daily Needs");
        assertThat(category.isActive()).isFalse();
        assertThat(category.getUpdatedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void productOwnsBarcodeAndLockableBalance() {
        Category category = Category.create("Grocery", NOW);
        Product product = Product.create(
                data("Rice", ProductUnit.KILOGRAM, true),
                category,
                "2000000000015",
                true,
                new BigDecimal("10.500"),
                NOW);

        assertThat(product.getId()).isNotBlank();
        assertThat(product.isNew()).isTrue();
        assertThat(product.getName()).isEqualTo("Rice");
        assertThat(product.getReceiptName()).isEqualTo("Rice 1kg");
        assertThat(product.getSku()).isEqualTo("RICE-1");
        assertThat(product.getCategory()).isSameAs(category);
        assertThat(product.getUnit()).isEqualTo(ProductUnit.KILOGRAM);
        assertThat(product.getHsnCode()).isEqualTo("1006");
        assertThat(product.getGstRate()).isEqualByComparingTo("5.00");
        assertThat(product.getPurchaseCost()).isEqualByComparingTo("45.00");
        assertThat(product.getSellingPrice()).isEqualByComparingTo("50.00");
        assertThat(product.getMinimumStockLevel()).isEqualByComparingTo("3.000");
        assertThat(product.isActive()).isTrue();
        assertThat(product.getVersion()).isZero();
        assertThat(product.getCreatedAt()).isEqualTo(NOW);

        ProductBarcode barcode = product.getBarcode();
        assertThat(barcode.getId()).isNotBlank();
        assertThat(barcode.getProduct()).isSameAs(product);
        assertThat(barcode.getValue()).isEqualTo("2000000000015");
        assertThat(barcode.isInternal()).isTrue();
        assertThat(barcode.getCreatedAt()).isEqualTo(NOW);

        InventoryBalance balance = product.getStockBalance();
        assertThat(balance.getProductId()).isEqualTo(product.getId());
        assertThat(balance.getProduct()).isSameAs(product);
        assertThat(balance.getQuantity()).isEqualByComparingTo("10.500");
        assertThat(balance.getVersion()).isZero();
        assertThat(balance.getUpdatedAt()).isEqualTo(NOW);

        assertThat(balance.adjust(new BigDecimal("1.250"), NOW.plusSeconds(2)))
                .isEqualByComparingTo("11.750");
        assertThatThrownBy(() -> balance.adjust(new BigDecimal("-20.000"), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void productUpdateAndStockTransactionRetainAllFields() {
        Category original = Category.create("Original", NOW);
        Category changed = Category.create("Changed", NOW);
        Product product = Product.create(
                data("Rice", ProductUnit.KILOGRAM, true),
                original,
                "1111",
                false,
                BigDecimal.ZERO,
                NOW);

        product.update(
                data("Flour", ProductUnit.PACKET, false),
                changed,
                "2222",
                true,
                NOW.plusSeconds(10));

        assertThat(product.getName()).isEqualTo("Flour");
        assertThat(product.getCategory()).isSameAs(changed);
        assertThat(product.isActive()).isFalse();
        assertThat(product.getBarcode().getValue()).isEqualTo("2222");
        assertThat(product.getBarcode().isInternal()).isTrue();
        assertThat(product.getUpdatedAt()).isEqualTo(NOW.plusSeconds(10));
        product.markNotNew();
        assertThat(product.isNew()).isFalse();

        StockTransaction transaction = StockTransaction.create(
                product,
                StockTransactionType.ADJUSTMENT,
                new BigDecimal("-1.000"),
                new BigDecimal("4.000"),
                StockReasonCode.DAMAGE,
                "COUNT",
                "count-1",
                "Damaged packet",
                "actor-1",
                NOW);

        assertThat(transaction.getId()).isNotBlank();
        assertThat(transaction.isNew()).isTrue();
        assertThat(transaction.getProduct()).isSameAs(product);
        assertThat(transaction.getTransactionType()).isEqualTo(StockTransactionType.ADJUSTMENT);
        assertThat(transaction.getQuantityDelta()).isEqualByComparingTo("-1.000");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("4.000");
        assertThat(transaction.getReasonCode()).isEqualTo(StockReasonCode.DAMAGE);
        assertThat(transaction.getReferenceType()).isEqualTo("COUNT");
        assertThat(transaction.getReferenceId()).isEqualTo("count-1");
        assertThat(transaction.getNotes()).isEqualTo("Damaged packet");
        assertThat(transaction.getActorUserId()).isEqualTo("actor-1");
        assertThat(transaction.getOccurredAt()).isEqualTo(NOW);
        transaction.markNotNew();
        assertThat(transaction.isNew()).isFalse();
    }

    @Test
    void unitMetadataAndBarcodeSelectionRulesAreExplicit() {
        assertThat(ProductUnit.KILOGRAM.getDisplayName()).isEqualTo("Kilogram");
        assertThat(ProductUnit.KILOGRAM.getSymbol()).isEqualTo("kg");
        assertThat(ProductUnit.KILOGRAM.isDecimalAllowed()).isTrue();
        assertThat(ProductUnit.PIECE.isDecimalAllowed()).isFalse();

        assertThat(request("1234", false).isBarcodeSelectionValid()).isTrue();
        assertThat(request(null, true).isBarcodeSelectionValid()).isTrue();
        assertThat(request("1234", true).isBarcodeSelectionValid()).isFalse();
        assertThat(request(" ", false).isBarcodeSelectionValid()).isFalse();
    }

    private ProductData data(String name, ProductUnit unit, boolean active) {
        return new ProductData(
                name,
                name + " 1kg",
                "RICE-1",
                unit,
                "1006",
                new BigDecimal("5.00"),
                new BigDecimal("45.00"),
                new BigDecimal("50.00"),
                new BigDecimal("3.000"),
                active);
    }

    private ProductCreateRequest request(String barcode, boolean generate) {
        return new ProductCreateRequest(
                "Rice",
                "Rice",
                "RICE-1",
                barcode,
                generate,
                "category-1",
                ProductUnit.PIECE,
                "1006",
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
