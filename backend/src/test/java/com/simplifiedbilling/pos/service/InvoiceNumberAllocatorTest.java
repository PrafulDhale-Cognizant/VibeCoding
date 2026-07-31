package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceNumberAllocatorTest {

    @Test
    void locksIncrementsAndFormatsSequence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InvoiceNumberAllocator allocator = new InvoiceNumberAllocator(jdbc);
        when(jdbc.queryForObject(
                "SELECT next_value FROM invoice_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class,
                "INVOICE")).thenReturn(12L, 13L, 14L);

        assertThat(allocator.next(" sale / ")).isEqualTo("SALE-000012");
        assertThat(allocator.next(" ")).isEqualTo("INV-000013");
        assertThat(allocator.next("!@#")).isEqualTo("INV-000014");
        verify(jdbc).update(
                "UPDATE invoice_sequences SET next_value = ? WHERE sequence_name = ?",
                13L,
                "INVOICE");
    }

    @Test
    void reportsUnavailableSequence() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        InvoiceNumberAllocator allocator = new InvoiceNumberAllocator(jdbc);
        when(jdbc.queryForObject(
                "SELECT next_value FROM invoice_sequences WHERE sequence_name = ? FOR UPDATE",
                Long.class,
                "INVOICE")).thenReturn(null);

        assertThatThrownBy(() -> allocator.next("INV"))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("INVOICE_SEQUENCE_UNAVAILABLE"));
    }
}
