package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.inventory.domain.ProductUnit;
import com.simplifiedbilling.inventory.service.CheckoutInventoryService;
import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.khata.service.CreditAccountService;
import com.simplifiedbilling.pos.domain.Invoice;
import com.simplifiedbilling.pos.domain.InvoiceStatus;
import com.simplifiedbilling.pos.domain.PaymentAllocation;
import com.simplifiedbilling.pos.domain.PaymentMode;
import com.simplifiedbilling.pos.domain.PricingLine;
import com.simplifiedbilling.pos.domain.PricingResult;
import com.simplifiedbilling.pos.domain.ReturnDisposition;
import com.simplifiedbilling.pos.domain.TaxMode;
import com.simplifiedbilling.pos.dto.SaleReturnRequests;
import com.simplifiedbilling.pos.repository.InvoiceRepository;
import com.simplifiedbilling.pos.repository.RefundRecordRepository;
import com.simplifiedbilling.pos.repository.SaleReturnItemRepository;
import com.simplifiedbilling.pos.repository.SaleReturnLineTotals;
import com.simplifiedbilling.pos.repository.SaleReturnRepository;
import com.simplifiedbilling.pos.service.SaleReturnNumberAllocator;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultSaleReturnServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-02T06:00:00Z");
    @Mock InvoiceRepository invoiceRepository;
    @Mock SaleReturnRepository returnRepository;
    @Mock SaleReturnItemRepository returnItemRepository;
    @Mock RefundRecordRepository refundRepository;
    @Mock CheckoutInventoryService inventoryService;
    @Mock CreditAccountService creditAccountService;
    @Mock SaleReturnNumberAllocator numberAllocator;
    @Mock AuditWriter auditWriter;
    @Mock SaleReturnLineTotals totals;
    private DefaultSaleReturnService service;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        service = new DefaultSaleReturnService(invoiceRepository, returnRepository, returnItemRepository,
                refundRepository, inventoryService, creditAccountService, numberAllocator, auditWriter,
                Clock.fixed(NOW, ZoneOffset.UTC));
        invoice = invoice();
        when(returnRepository.findByIdempotencyKey("return-key-1")).thenReturn(Optional.empty());
        when(invoiceRepository.findDetailedByIdForUpdate(invoice.getId())).thenReturn(Optional.of(invoice));
        when(returnItemRepository.returnedTotals(invoice.getItems().getFirst().getId())).thenReturn(totals);
        zeroTotals();
        when(returnRepository.returnedTotal(invoice.getId())).thenReturn(money("0"));
        when(numberAllocator.next()).thenReturn("SR-2026-000001");
    }

    @Test
    void completesFullSaleableReturnAndRestoresStock() {
        var response = service.returnItems("actor", invoice.getId(), "return-key-1",
                request("2", ReturnDisposition.SALEABLE, "118"));

        assertThat(response.returnNumber()).isEqualTo("SR-2026-000001");
        assertThat(response.totalAmount()).isEqualByComparingTo("118.00");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.RETURNED);
        verify(inventoryService).restoreSaleableReturns(eq("actor"), any(), any());
        verify(returnRepository).saveAndFlush(any());
        verify(auditWriter).write(eq("actor"), eq("SALE_RETURNED"), eq("INVOICE"), eq(invoice.getId()), any());
    }

    @Test
    void damagedPartialReturnDoesNotIncreaseSaleableStock() {
        var response = service.returnItems("actor", invoice.getId(), "return-key-1",
                request("1", ReturnDisposition.DAMAGED, "59"));

        assertThat(response.items()).singleElement().satisfies(line ->
                assertThat(line.disposition()).isEqualTo(ReturnDisposition.DAMAGED));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_RETURNED);
        verify(inventoryService).restoreSaleableReturns(eq("actor"), any(), eq(List.of()));
    }

    @Test
    void rejectsCumulativeQuantityAboveSoldQuantity() {
        when(totals.getQuantity()).thenReturn(quantity("1.500"));
        assertThatThrownBy(() -> service.returnItems("actor", invoice.getId(), "return-key-1",
                request("1", ReturnDisposition.SALEABLE, "59")))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("returnable");
    }

    private SaleReturnRequests.CreateRequest request(String quantity, ReturnDisposition disposition, String refund) {
        return new SaleReturnRequests.CreateRequest(
                List.of(new SaleReturnRequests.LineRequest(invoice.getItems().getFirst().getId(),
                        quantity(quantity), disposition)),
                List.of(new SaleReturnRequests.RefundRequest(PaymentMode.CASH, money(refund), null, null)),
                "Customer return");
    }

    private Invoice invoice() {
        var product = new SaleProductSnapshot("product", "Rice", "Rice", "123", ProductUnit.PIECE,
                money("18"), money("30"), money("59"), quantity("10"));
        var line = new PricingLine(1, product, quantity("2"), money("118"), money("0"), money("0"),
                money("100"), money("9"), money("9"), money("0"), money("118"));
        var pricing = new PricingResult(List.of(line), TaxMode.INTRA_STATE, true, "27ABCDE1234F1Z5", money("118"), money("0"),
                money("0"), money("100"), money("9"), money("9"), money("0"), money("0"), money("118"));
        return Invoice.completed("invoice", "INV-1", "checkout-key", "cashier", pricing,
                List.of(new PaymentAllocation(PaymentMode.CASH, money("118"), money("118"), money("0"),
                        null, null, null)), null, NOW.minusSeconds(60));
    }

    private void zeroTotals() {
        when(totals.getQuantity()).thenReturn(quantity("0"));
        when(totals.getGrossAmount()).thenReturn(money("0"));
        when(totals.getDiscountAmount()).thenReturn(money("0"));
        when(totals.getTaxableAmount()).thenReturn(money("0"));
        when(totals.getCgstAmount()).thenReturn(money("0"));
        when(totals.getSgstAmount()).thenReturn(money("0"));
        when(totals.getIgstAmount()).thenReturn(money("0"));
        when(totals.getLineTotal()).thenReturn(money("0"));
    }
    private BigDecimal money(String value) { return new BigDecimal(value).setScale(2); }
    private BigDecimal quantity(String value) { return new BigDecimal(value).setScale(3); }
}
