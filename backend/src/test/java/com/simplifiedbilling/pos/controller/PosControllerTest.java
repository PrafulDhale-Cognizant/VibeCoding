package com.simplifiedbilling.pos.controller;

import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.pos.dto.PosResponses;
import com.simplifiedbilling.pos.service.PosService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PosControllerTest {

    @Test
    void delegatesQuoteCheckoutAndInvoiceLookup() {
        PosService service = mock(PosService.class);
        PosController controller = new PosController(service);
        PosRequests.QuoteRequest quoteRequest = mock(PosRequests.QuoteRequest.class);
        PosRequests.CheckoutRequest checkoutRequest = mock(PosRequests.CheckoutRequest.class);
        PosResponses.QuoteResponse quoteResponse = mock(PosResponses.QuoteResponse.class);
        PosResponses.InvoiceResponse invoiceResponse = mock(PosResponses.InvoiceResponse.class);
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("actor");
        when(service.quote(quoteRequest)).thenReturn(quoteResponse);
        when(service.checkout("actor", "checkout-key", checkoutRequest)).thenReturn(invoiceResponse);
        when(service.getInvoice("invoice-id")).thenReturn(invoiceResponse);

        assertThat(controller.quote(quoteRequest)).isSameAs(quoteResponse);
        assertThat(controller.checkout(jwt, "checkout-key", checkoutRequest)).isSameAs(invoiceResponse);
        assertThat(controller.getInvoice("invoice-id")).isSameAs(invoiceResponse);
    }
}
