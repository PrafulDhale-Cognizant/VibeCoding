package com.simplifiedbilling.khata.mapper;

import com.simplifiedbilling.khata.domain.Customer;
import com.simplifiedbilling.khata.domain.KhataLedgerEntry;
import com.simplifiedbilling.khata.dto.KhataResponses;
import org.springframework.stereotype.Component;

@Component
public class KhataMapper {

    public KhataResponses.CustomerResponse toCustomer(Customer customer) {
        return new KhataResponses.CustomerResponse(
                customer.getId(), customer.getName(), customer.getPhone(), customer.getNotes(),
                customer.isActive(), customer.getCreditBalance().getOutstandingAmount(),
                customer.getVersion(), customer.getCreditBalance().getVersion(),
                customer.getCreatedAt(), customer.getUpdatedAt());
    }

    public KhataResponses.LedgerEntryResponse toEntry(KhataLedgerEntry entry) {
        return new KhataResponses.LedgerEntryResponse(
                entry.getId(), entry.getCustomer().getId(), entry.getEntryType(), entry.getAmount(),
                entry.getBalanceAfter(), entry.getInvoiceId(), entry.getPaymentMode(),
                entry.getPaymentReference(), entry.getNotes(), entry.getActorUserId(),
                entry.getOccurredAt());
    }

    public KhataResponses.SettlementResponse toSettlement(
            KhataLedgerEntry entry,
            boolean idempotentReplay) {
        return new KhataResponses.SettlementResponse(
                entry.getId(), entry.getCustomer().getId(), entry.getAmount(),
                entry.getBalanceAfter(), entry.getPaymentMode(), entry.getOccurredAt(),
                idempotentReplay);
    }
}
