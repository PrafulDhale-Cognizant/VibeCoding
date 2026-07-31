package com.simplifiedbilling.inventory.service;

import com.simplifiedbilling.inventory.dto.BarcodeResponse;

public interface BarcodeService {

    BarcodeResponse generateBarcode(String actorUserId);
}
