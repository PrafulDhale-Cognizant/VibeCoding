package com.simplifiedbilling.purchasing.controller;

import com.simplifiedbilling.purchasing.domain.SupplierBalanceStatus;
import com.simplifiedbilling.purchasing.dto.PurchasingPage;
import com.simplifiedbilling.purchasing.dto.PurchasingRequests;
import com.simplifiedbilling.purchasing.dto.PurchasingResponses;
import com.simplifiedbilling.purchasing.service.PurchasingService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchasingControllerTest {

    @Test
    void delegatesEverySupplierPurchaseAndPaymentEndpoint() {
        PurchasingService service = mock(PurchasingService.class);
        PurchasingController controller = new PurchasingController(service);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        PurchasingRequests.CreateSupplierRequest create = mock(PurchasingRequests.CreateSupplierRequest.class);
        PurchasingRequests.UpdateSupplierRequest update = mock(PurchasingRequests.UpdateSupplierRequest.class);
        PurchasingRequests.SupplierPaymentRequest payment = mock(PurchasingRequests.SupplierPaymentRequest.class);
        PurchasingRequests.ReceivePurchaseRequest receive = mock(PurchasingRequests.ReceivePurchaseRequest.class);
        PurchasingResponses.SupplierResponse supplier = mock(PurchasingResponses.SupplierResponse.class);
        PurchasingResponses.SummaryResponse summary = mock(PurchasingResponses.SummaryResponse.class);
        PurchasingResponses.SupplierPaymentResponse paid = mock(PurchasingResponses.SupplierPaymentResponse.class);
        PurchasingResponses.PurchaseResponse purchase = mock(PurchasingResponses.PurchaseResponse.class);
        @SuppressWarnings("unchecked")
        PurchasingPage<PurchasingResponses.SupplierResponse> suppliers = mock(PurchasingPage.class);
        @SuppressWarnings("unchecked")
        PurchasingPage<PurchasingResponses.SupplierLedgerResponse> statement = mock(PurchasingPage.class);
        @SuppressWarnings("unchecked")
        PurchasingPage<PurchasingResponses.PurchaseSummaryResponse> purchases = mock(PurchasingPage.class);
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 2);

        when(service.getSummary()).thenReturn(summary);
        when(service.searchSuppliers("fresh", true, SupplierBalanceStatus.DUE, 0, 25)).thenReturn(suppliers);
        when(service.createSupplier("actor", create)).thenReturn(supplier);
        when(service.getSupplier("supplier")).thenReturn(supplier);
        when(service.updateSupplier("actor", "supplier", update)).thenReturn(supplier);
        when(service.getSupplierStatement("supplier", 0, 50)).thenReturn(statement);
        when(service.paySupplier("actor", "supplier", "payment-key", payment)).thenReturn(paid);
        when(service.searchPurchases("pur", "supplier", from, to, 0, 25)).thenReturn(purchases);
        when(service.receivePurchase("actor", "purchase-key", receive)).thenReturn(purchase);
        when(service.getPurchase("purchase")).thenReturn(purchase);

        assertThat(controller.summary()).isSameAs(summary);
        assertThat(controller.suppliers("fresh", true, SupplierBalanceStatus.DUE, 0, 25)).isSameAs(suppliers);
        assertThat(controller.createSupplier(jwt, create)).isSameAs(supplier);
        assertThat(controller.getSupplier("supplier")).isSameAs(supplier);
        assertThat(controller.updateSupplier(jwt, "supplier", update)).isSameAs(supplier);
        assertThat(controller.statement("supplier", 0, 50)).isSameAs(statement);
        assertThat(controller.paySupplier(jwt, "supplier", "payment-key", payment)).isSameAs(paid);
        assertThat(controller.purchases("pur", "supplier", from, to, 0, 25)).isSameAs(purchases);
        assertThat(controller.receivePurchase(jwt, "purchase-key", receive)).isSameAs(purchase);
        assertThat(controller.getPurchase("purchase")).isSameAs(purchase);
    }
}
