package com.simplifiedbilling.khata;

import com.simplifiedbilling.khata.domain.BalanceStatus;
import com.simplifiedbilling.khata.domain.KhataEntryType;
import com.simplifiedbilling.khata.domain.SettlementMode;
import com.simplifiedbilling.khata.dto.KhataRequests;
import com.simplifiedbilling.khata.service.CreditAccountService;
import com.simplifiedbilling.khata.service.KhataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class KhataFlowTest {

    @Autowired private KhataService khataService;
    @Autowired private CreditAccountService creditAccountService;

    @Test
    void persistsCustomerCreditSaleStatementAndIdempotentSettlement() {
        String actor = "khata-test-actor";
        var customer = khataService.createCustomer(
                actor,
                new KhataRequests.CreateCustomerRequest(
                        "Ravi Kumar", "+91 98765 43210", "Neighbourhood customer"));

        assertThat(creditAccountService.getCreditCustomer(customer.id()).phone())
                .isEqualTo("9876543210");
        creditAccountService.postCreditSale(
                actor, customer.id(), "invoice-khata-1", new BigDecimal("250.00"));

        var credited = khataService.getCustomer(customer.id());
        assertThat(credited.outstandingAmount()).isEqualByComparingTo("250.00");
        assertThat(khataService.searchCustomers(
                "98765", true, BalanceStatus.DUE, 0, 25).content())
                .extracting("id")
                .containsExactly(customer.id());

        var settled = khataService.settle(
                actor,
                customer.id(),
                "khata-settlement-key",
                new KhataRequests.SettlementRequest(
                        new BigDecimal("100.00"), SettlementMode.CASH,
                        null, "Partial collection", credited.balanceVersion()));
        assertThat(settled.balanceAfter()).isEqualByComparingTo("150.00");
        assertThat(settled.idempotentReplay()).isFalse();

        var replay = khataService.settle(
                actor,
                customer.id(),
                "khata-settlement-key",
                new KhataRequests.SettlementRequest(
                        new BigDecimal("100.00"), SettlementMode.CASH,
                        null, "Partial collection", credited.balanceVersion()));
        assertThat(replay.entryId()).isEqualTo(settled.entryId());
        assertThat(replay.idempotentReplay()).isTrue();

        assertThat(khataService.getStatement(customer.id(), 0, 25).content())
                .extracting("entryType")
                .containsExactly(KhataEntryType.SETTLEMENT, KhataEntryType.CREDIT_SALE);
        assertThat(khataService.getSummary().totalOutstanding()).isEqualByComparingTo("150.00");
    }
}
