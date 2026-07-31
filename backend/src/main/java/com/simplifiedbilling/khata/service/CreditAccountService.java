package com.simplifiedbilling.khata.service;

import java.math.BigDecimal;

/** Service boundary used by POS; checkout never accesses Khata repositories directly. */
public interface CreditAccountService {

    CreditCustomerSnapshot getCreditCustomer(String customerId);

    void postCreditSale(
            String actorUserId,
            String customerId,
            String invoiceId,
            BigDecimal amount);
}
