package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.repository.ProductBarcodeRepository;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalBarcodeAllocatorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ProductBarcodeRepository barcodeRepository;

    @Test
    void allocatesEan13AndAdvancesSequence() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("PRODUCT")))
                .thenReturn(1L);

        String barcode = allocator().allocate();

        assertThat(barcode).isEqualTo("2000000000015");
        verify(jdbcTemplate).update(anyString(), eq(2L), eq("PRODUCT"));
        verify(barcodeRepository).existsByValue(barcode);
    }

    @Test
    void skipsAnUnexpectedExistingBarcode() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("PRODUCT")))
                .thenReturn(1L, 2L);
        when(barcodeRepository.existsByValue("2000000000015")).thenReturn(true);

        assertThat(allocator().allocate()).isEqualTo("2000000000022");
    }

    @Test
    void rejectsInvalidOrExhaustedSequences() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), eq("PRODUCT")))
                .thenReturn(null);
        assertThatThrownBy(() -> allocator().allocate())
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BARCODE_SEQUENCE_EXHAUSTED"));

        assertThatThrownBy(() -> InternalBarcodeAllocator.toEan13(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternalBarcodeAllocator.toEan13(
                InternalBarcodeAllocator.MAX_SEQUENCE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private InternalBarcodeAllocator allocator() {
        return new InternalBarcodeAllocator(jdbcTemplate, barcodeRepository);
    }
}
