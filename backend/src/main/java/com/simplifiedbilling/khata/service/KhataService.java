package com.simplifiedbilling.khata.service;

import com.simplifiedbilling.khata.domain.BalanceStatus;
import com.simplifiedbilling.khata.dto.KhataPage;
import com.simplifiedbilling.khata.dto.KhataRequests;
import com.simplifiedbilling.khata.dto.KhataResponses;

public interface KhataService {

    KhataPage<KhataResponses.CustomerResponse> searchCustomers(
            String query, Boolean active, BalanceStatus balanceStatus, int page, int size);

    KhataResponses.CustomerResponse getCustomer(String customerId);

    KhataResponses.CustomerResponse createCustomer(
            String actorUserId, KhataRequests.CreateCustomerRequest request);

    KhataResponses.CustomerResponse updateCustomer(
            String actorUserId, String customerId, KhataRequests.UpdateCustomerRequest request);

    KhataPage<KhataResponses.LedgerEntryResponse> getStatement(
            String customerId, int page, int size);

    KhataResponses.SettlementResponse settle(
            String actorUserId,
            String customerId,
            String idempotencyKey,
            KhataRequests.SettlementRequest request);

    KhataResponses.SummaryResponse getSummary();
}
