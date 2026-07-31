package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.dto.PurchasingPage;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.dto.PurchasingResponses;

import java.time.LocalDate;

public interface PurchasingService {

    PurchasingPage<PurchasingResponses.SupplierResponse> searchSuppliers(
            String query, Boolean active, SupplierBalanceStatus balanceStatus, int page, int size);

    PurchasingResponses.SupplierResponse getSupplier(String supplierId);

    PurchasingResponses.SupplierResponse createSupplier(
            String actorUserId, PurchasingRequests.CreateSupplierRequest request);

    PurchasingResponses.SupplierResponse updateSupplier(
            String actorUserId, String supplierId, PurchasingRequests.UpdateSupplierRequest request);

    PurchasingResponses.SummaryResponse getSummary();

    PurchasingPage<PurchasingResponses.SupplierLedgerResponse> getSupplierStatement(
            String supplierId, int page, int size);

    PurchasingResponses.SupplierPaymentResponse paySupplier(
            String actorUserId, String supplierId, String idempotencyKey,
            PurchasingRequests.SupplierPaymentRequest request);

    PurchasingResponses.PurchaseResponse receivePurchase(
            String actorUserId, String idempotencyKey,
            PurchasingRequests.ReceivePurchaseRequest request);

    PurchasingResponses.PurchaseResponse getPurchase(String purchaseId);

    PurchasingPage<PurchasingResponses.PurchaseSummaryResponse> searchPurchases(
            String query, String supplierId, LocalDate from, LocalDate to, int page, int size);

    PurchasingResponses.PurchaseReturnResponse returnPurchase(
            String actorUserId, String purchaseId, String idempotencyKey,
            PurchasingRequests.CreatePurchaseReturnRequest request);

    PurchasingResponses.PurchaseReturnResponse getPurchaseReturn(String purchaseReturnId);

    PurchasingPage<PurchasingResponses.PurchaseReturnSummaryResponse> searchPurchaseReturns(
            String query, String supplierId, String purchaseId,
            LocalDate from, LocalDate to, int page, int size);

    PurchasingResponses.SupplierAnalyticsResponse getSupplierAnalytics(
            LocalDate from, LocalDate to);
}
