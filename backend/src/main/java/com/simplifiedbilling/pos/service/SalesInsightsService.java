package com.simplifiedbilling.pos.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SalesInsightsService {
    List<TopProductSnapshot> getTopProducts(Instant startInclusive, Instant endExclusive, int limit);
    List<RecentTransactionSnapshot> getRecentTransactions(int limit);

    record TopProductSnapshot(String productId, String productName, BigDecimal quantity, BigDecimal netSales) { }
    record RecentTransactionSnapshot(
            String id, String referenceNumber, String type, Instant occurredAt,
            BigDecimal amount, String customerName) { }
}
