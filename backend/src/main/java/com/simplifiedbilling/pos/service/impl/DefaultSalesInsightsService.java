package com.simplifiedbilling.pos.service.impl;

import com.simplifiedbilling.pos.repository.SalesReportQueryRepository;
import com.simplifiedbilling.pos.service.SalesInsightsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class DefaultSalesInsightsService implements SalesInsightsService {
    private final SalesReportQueryRepository repository;

    public DefaultSalesInsightsService(SalesReportQueryRepository repository) { this.repository = repository; }

    @Override
    @Transactional(readOnly = true)
    public List<TopProductSnapshot> getTopProducts(Instant startInclusive, Instant endExclusive, int limit) {
        return repository.findTopProducts(startInclusive, endExclusive, limit).stream()
                .map(row -> new TopProductSnapshot(row.productId(), row.productName(), row.quantity(), row.netSales()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentTransactionSnapshot> getRecentTransactions(int limit) {
        return repository.findRecentTransactions(limit).stream()
                .map(row -> new RecentTransactionSnapshot(row.id(), row.referenceNumber(), row.type(),
                        row.occurredAt(), row.amount(), row.customerName())).toList();
    }
}
