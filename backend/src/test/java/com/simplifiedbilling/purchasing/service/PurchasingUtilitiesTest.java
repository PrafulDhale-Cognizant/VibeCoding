package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchasingUtilitiesTest {

    @Test
    void normalizesIndianSupplierMobileNumbers() {
        SupplierPhoneNormalizer normalizer = new SupplierPhoneNormalizer();

        assertThat(normalizer.normalize("+91 98765-43210")).isEqualTo("9876543210");
        assertThat(normalizer.normalize("09876543210")).isEqualTo("9876543210");
        assertThat(normalizer.normalize("9876543210")).isEqualTo("9876543210");
        assertThatThrownBy(() -> normalizer.normalize("12345"))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVALID_SUPPLIER_PHONE"));
    }

    @Test
    void allocatesMonotonicPurchaseNumberUnderDatabaseLock() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                "SELECT next_value FROM purchase_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class, "PURCHASE")).thenReturn(42L);
        PurchaseNumberAllocator allocator = new PurchaseNumberAllocator(jdbc);

        assertThat(allocator.next()).isEqualTo("PUR-000042");
        verify(jdbc).update(
                "UPDATE purchase_sequences SET next_value = ? WHERE sequence_name = ?",
                43L, "PURCHASE");
    }

    @Test
    void failsClearlyWhenPurchaseSequenceIsUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                "SELECT next_value FROM purchase_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class, "PURCHASE")).thenReturn(null);

        assertThatThrownBy(() -> new PurchaseNumberAllocator(jdbc).next())
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PURCHASE_SEQUENCE_UNAVAILABLE"));
    }

    @Test
    void allocatesReturnNumberAndFailsClearlyWhenSequenceIsUnavailable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(
                "SELECT next_value FROM purchase_return_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class, "PURCHASE_RETURN")).thenReturn(7L);
        PurchaseReturnNumberAllocator allocator = new PurchaseReturnNumberAllocator(jdbc);

        assertThat(allocator.next()).isEqualTo("PRN-000007");
        verify(jdbc).update(
                "UPDATE purchase_return_sequences SET next_value = ? WHERE sequence_name = ?",
                8L, "PURCHASE_RETURN");

        when(jdbc.queryForObject(
                "SELECT next_value FROM purchase_return_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class, "PURCHASE_RETURN")).thenReturn(null);
        assertThatThrownBy(allocator::next)
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("PURCHASE_RETURN_SEQUENCE_UNAVAILABLE"));
    }
}
