package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.service.InternalBarcodeAllocator;
import com.simplifiedbilling.shared.audit.AuditWriter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultBarcodeServiceTest {

    @Test
    void allocatesAndAuditsBarcode() {
        InternalBarcodeAllocator allocator = mock(InternalBarcodeAllocator.class);
        AuditWriter auditWriter = mock(AuditWriter.class);
        when(allocator.allocate()).thenReturn("2000000000015");
        DefaultBarcodeService service = new DefaultBarcodeService(allocator, auditWriter);

        assertThat(service.generateBarcode("actor").barcode()).isEqualTo("2000000000015");
        verify(auditWriter).write(
                "actor",
                "INTERNAL_BARCODE_ALLOCATED",
                "PRODUCT_BARCODE",
                "2000000000015",
                Map.of("barcode", "2000000000015"));
    }
}
