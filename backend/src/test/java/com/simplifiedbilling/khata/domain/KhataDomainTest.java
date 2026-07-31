package com.simplifiedbilling.khata.domain;

import com.simplifiedbilling.khata.mapper.KhataMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KhataDomainTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    @Test
    void customerOwnsVersionedCreditBalanceAndCanBeUpdated() {
        Customer customer = Customer.create("Ravi Kumar", "9876543210", "Regular", NOW);

        assertThat(customer.getId()).isNotBlank();
        assertThat(customer.isNew()).isTrue();
        assertThat(customer.getName()).isEqualTo("Ravi Kumar");
        assertThat(customer.getPhone()).isEqualTo("9876543210");
        assertThat(customer.getNotes()).isEqualTo("Regular");
        assertThat(customer.isActive()).isTrue();
        assertThat(customer.getVersion()).isZero();
        assertThat(customer.getCreatedAt()).isEqualTo(NOW);
        assertThat(customer.getUpdatedAt()).isEqualTo(NOW);

        CustomerCreditBalance balance = customer.getCreditBalance();
        assertThat(balance.getCustomerId()).isEqualTo(customer.getId());
        assertThat(balance.getCustomer()).isSameAs(customer);
        assertThat(balance.getOutstandingAmount()).isZero();
        assertThat(balance.getVersion()).isZero();
        assertThat(balance.addCredit(new BigDecimal("125.50"), NOW.plusSeconds(1)))
                .isEqualByComparingTo("125.50");
        assertThat(balance.settle(new BigDecimal("25.50"), NOW.plusSeconds(2)))
                .isEqualByComparingTo("100.00");
        assertThat(balance.getUpdatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThatThrownBy(() -> balance.settle(new BigDecimal("100.01"), NOW))
                .isInstanceOf(IllegalArgumentException.class);

        customer.update("Ravi K", "9999999999", null, false, NOW.plusSeconds(3));
        assertThat(customer.getName()).isEqualTo("Ravi K");
        assertThat(customer.getPhone()).isEqualTo("9999999999");
        assertThat(customer.getNotes()).isNull();
        assertThat(customer.isActive()).isFalse();
        assertThat(customer.getUpdatedAt()).isEqualTo(NOW.plusSeconds(3));
        customer.markNotNew();
        assertThat(customer.isNew()).isFalse();
    }

    @Test
    void ledgerFactoriesCreateImmutableCreditAndSettlementEntries() {
        Customer customer = Customer.create("Ravi", "9876543210", null, NOW);
        KhataLedgerEntry credit = KhataLedgerEntry.creditSale(
                customer, new BigDecimal("100.00"), new BigDecimal("100.00"),
                "invoice-1", "actor", NOW);

        assertThat(credit.getId()).isNotBlank();
        assertThat(credit.isNew()).isTrue();
        assertThat(credit.getCustomer()).isSameAs(customer);
        assertThat(credit.getEntryType()).isEqualTo(KhataEntryType.CREDIT_SALE);
        assertThat(credit.getAmount()).isEqualByComparingTo("100.00");
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(credit.getInvoiceId()).isEqualTo("invoice-1");
        assertThat(credit.getIdempotencyKey()).isNull();
        assertThat(credit.getPaymentMode()).isNull();
        assertThat(credit.getPaymentReference()).isNull();
        assertThat(credit.getNotes()).isEqualTo("Udhaar sale");
        assertThat(credit.getActorUserId()).isEqualTo("actor");
        assertThat(credit.getOccurredAt()).isEqualTo(NOW);
        credit.markNotNew();
        assertThat(credit.isNew()).isFalse();

        KhataLedgerEntry settlement = KhataLedgerEntry.settlement(
                customer, new BigDecimal("40.00"), new BigDecimal("60.00"),
                SettlementMode.UPI, "settlement-key", "UPI-123", "Partial payment", "actor", NOW);
        assertThat(settlement.getEntryType()).isEqualTo(KhataEntryType.SETTLEMENT);
        assertThat(settlement.getInvoiceId()).isNull();
        assertThat(settlement.getIdempotencyKey()).isEqualTo("settlement-key");
        assertThat(settlement.getPaymentMode()).isEqualTo(SettlementMode.UPI);
        assertThat(settlement.getPaymentReference()).isEqualTo("UPI-123");
        assertThat(settlement.getNotes()).isEqualTo("Partial payment");
    }

    @Test
    void mapperReturnsCustomerStatementAndSettlementDtos() {
        Customer customer = Customer.create("Ravi", "9876543210", "Note", NOW);
        customer.getCreditBalance().addCredit(new BigDecimal("80.00"), NOW);
        KhataLedgerEntry entry = KhataLedgerEntry.settlement(
                customer, new BigDecimal("20.00"), new BigDecimal("60.00"),
                SettlementMode.CASH, "settlement-key", null, null, "actor", NOW);
        KhataMapper mapper = new KhataMapper();

        var customerResponse = mapper.toCustomer(customer);
        var entryResponse = mapper.toEntry(entry);
        var settlement = mapper.toSettlement(entry, true);

        assertThat(customerResponse.outstandingAmount()).isEqualByComparingTo("80.00");
        assertThat(customerResponse.balanceVersion()).isZero();
        assertThat(entryResponse.customerId()).isEqualTo(customer.getId());
        assertThat(entryResponse.entryType()).isEqualTo(KhataEntryType.SETTLEMENT);
        assertThat(settlement.entryId()).isEqualTo(entry.getId());
        assertThat(settlement.balanceAfter()).isEqualByComparingTo("60.00");
        assertThat(settlement.idempotentReplay()).isTrue();
    }
}
