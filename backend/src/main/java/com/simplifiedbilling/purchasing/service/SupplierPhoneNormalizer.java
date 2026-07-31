package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SupplierPhoneNormalizer {

    public String normalize(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (!digits.matches("[6-9][0-9]{9}")) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SUPPLIER_PHONE",
                    "Enter a valid 10-digit Indian supplier mobile number.");
        }
        return digits;
    }
}
