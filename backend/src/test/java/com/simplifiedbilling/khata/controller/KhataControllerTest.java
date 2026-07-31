package com.simplifiedbilling.khata.controller;

import com.simplifiedbilling.khata.domain.BalanceStatus;
import com.simplifiedbilling.khata.dto.KhataPage;
import com.simplifiedbilling.khata.dto.KhataRequests;
import com.simplifiedbilling.khata.dto.KhataResponses;
import com.simplifiedbilling.khata.service.KhataService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KhataControllerTest {

    @Test
    void delegatesEveryCustomerStatementAndSettlementEndpoint() {
        KhataService service = mock(KhataService.class);
        KhataController controller = new KhataController(service);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        KhataRequests.CreateCustomerRequest create = mock(KhataRequests.CreateCustomerRequest.class);
        KhataRequests.UpdateCustomerRequest update = mock(KhataRequests.UpdateCustomerRequest.class);
        KhataRequests.SettlementRequest settlement = mock(KhataRequests.SettlementRequest.class);
        KhataResponses.CustomerResponse customer = mock(KhataResponses.CustomerResponse.class);
        KhataResponses.SettlementResponse settled = mock(KhataResponses.SettlementResponse.class);
        KhataResponses.SummaryResponse summary = mock(KhataResponses.SummaryResponse.class);
        @SuppressWarnings("unchecked")
        KhataPage<KhataResponses.CustomerResponse> customers = mock(KhataPage.class);
        @SuppressWarnings("unchecked")
        KhataPage<KhataResponses.LedgerEntryResponse> statement = mock(KhataPage.class);

        when(service.getSummary()).thenReturn(summary);
        when(service.searchCustomers("ravi", true, BalanceStatus.DUE, 0, 25)).thenReturn(customers);
        when(service.getCustomer("customer")).thenReturn(customer);
        when(service.createCustomer("actor", create)).thenReturn(customer);
        when(service.updateCustomer("actor", "customer", update)).thenReturn(customer);
        when(service.getStatement("customer", 0, 50)).thenReturn(statement);
        when(service.settle("actor", "customer", "settlement-key", settlement)).thenReturn(settled);

        assertThat(controller.summary()).isSameAs(summary);
        assertThat(controller.searchCustomers("ravi", true, BalanceStatus.DUE, 0, 25)).isSameAs(customers);
        assertThat(controller.getCustomer("customer")).isSameAs(customer);
        assertThat(controller.createCustomer(jwt, create)).isSameAs(customer);
        assertThat(controller.updateCustomer(jwt, "customer", update)).isSameAs(customer);
        assertThat(controller.statement("customer", 0, 50)).isSameAs(statement);
        assertThat(controller.settle(jwt, "customer", "settlement-key", settlement)).isSameAs(settled);
    }
}
