package com.simplifiedbilling.pos.service;

import java.time.Instant;
import java.time.ZoneId;

public interface SalesReportingService {

    SalesReportSnapshot getSalesReport(
            Instant startInclusive,
            Instant endExclusive,
            ZoneId businessZone);
}
