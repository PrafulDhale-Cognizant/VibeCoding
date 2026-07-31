package com.simplifiedbilling.inventory.service.impl;

import com.simplifiedbilling.inventory.dto.BarcodeResponse;
import com.simplifiedbilling.inventory.service.BarcodeService;
import com.simplifiedbilling.inventory.service.InternalBarcodeAllocator;
import com.simplifiedbilling.shared.audit.AuditWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DefaultBarcodeService implements BarcodeService {

    private final InternalBarcodeAllocator allocator;
    private final AuditWriter auditWriter;

    public DefaultBarcodeService(InternalBarcodeAllocator allocator, AuditWriter auditWriter) {
        this.allocator = allocator;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public BarcodeResponse generateBarcode(String actorUserId) {
        String barcode = allocator.allocate();
        auditWriter.write(
                actorUserId,
                "INTERNAL_BARCODE_ALLOCATED",
                "PRODUCT_BARCODE",
                barcode,
                Map.of("barcode", barcode));
        return new BarcodeResponse(barcode);
    }
}
