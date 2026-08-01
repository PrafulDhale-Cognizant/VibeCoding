package com.simplifiedbilling.khata.service.impl;

import com.simplifiedbilling.khata.domain.BalanceStatus;
import com.simplifiedbilling.khata.domain.Customer;
import com.simplifiedbilling.khata.domain.CustomerCreditBalance;
import com.simplifiedbilling.khata.domain.KhataLedgerEntry;
import com.simplifiedbilling.khata.dto.KhataPage;
import com.simplifiedbilling.khata.dto.KhataRequests;
import com.simplifiedbilling.khata.dto.KhataResponses;
import com.simplifiedbilling.khata.mapper.KhataMapper;
import com.simplifiedbilling.khata.repository.CustomerCreditBalanceRepository;
import com.simplifiedbilling.khata.repository.CustomerRepository;
import com.simplifiedbilling.khata.repository.KhataLedgerRepository;
import com.simplifiedbilling.khata.service.CreditAccountService;
import com.simplifiedbilling.khata.service.CreditCustomerSnapshot;
import com.simplifiedbilling.khata.service.CustomerPhoneNormalizer;
import com.simplifiedbilling.khata.service.KhataService;
import com.simplifiedbilling.shared.audit.AuditWriter;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DefaultKhataService implements KhataService, CreditAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{8,80}");

    private final CustomerRepository customerRepository;
    private final CustomerCreditBalanceRepository balanceRepository;
    private final KhataLedgerRepository ledgerRepository;
    private final CustomerPhoneNormalizer phoneNormalizer;
    private final KhataMapper mapper;
    private final AuditWriter auditWriter;
    private final Clock clock;

    public DefaultKhataService(
            CustomerRepository customerRepository,
            CustomerCreditBalanceRepository balanceRepository,
            KhataLedgerRepository ledgerRepository,
            CustomerPhoneNormalizer phoneNormalizer,
            KhataMapper mapper,
            AuditWriter auditWriter,
            Clock clock) {
        this.customerRepository = customerRepository;
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.phoneNormalizer = phoneNormalizer;
        this.mapper = mapper;
        this.auditWriter = auditWriter;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public KhataPage<KhataResponses.CustomerResponse> searchCustomers(
            String query,
            Boolean active,
            BalanceStatus requestedStatus,
            int page,
            int size) {
        validatePage(page, size);
        BalanceStatus status = requestedStatus == null ? BalanceStatus.ALL : requestedStatus;
        var customers = customerRepository.search(
                searchPattern(query), active, status.name(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return KhataPage.from(customers, mapper::toCustomer);
    }

    @Override
    @Transactional(readOnly = true)
    public KhataResponses.CustomerResponse getCustomer(String customerId) {
        return mapper.toCustomer(requireCustomer(customerId));
    }

    @Override
    @Transactional
    public KhataResponses.CustomerResponse createCustomer(
            String actorUserId,
            KhataRequests.CreateCustomerRequest request) {
        String phone = phoneNormalizer.normalize(request.phone());
        ensurePhoneAvailable(phone, null);
        Instant now = Instant.now(clock);
        Customer customer = Customer.create(
                normalizeName(request.name()), phone, normalizeText(request.notes()), now);
        customerRepository.saveAndFlush(customer);
        auditWriter.write(
                actorUserId, "CUSTOMER_CREATED", "CUSTOMER", customer.getId(),
                Map.of("name", customer.getName(), "phone", customer.getPhone()));
        return mapper.toCustomer(customer);
    }

    @Override
    @Transactional
    public KhataResponses.CustomerResponse updateCustomer(
            String actorUserId,
            String customerId,
            KhataRequests.UpdateCustomerRequest request) {
        Customer customer = requireCustomer(customerId);
        if (customer.getVersion() != request.version()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_CUSTOMER_VERSION",
                    "Customer details have changed. Refresh and try again.");
        }
        String phone = phoneNormalizer.normalize(request.phone());
        ensurePhoneAvailable(phone, customerId);
        customer.update(
                normalizeName(request.name()), phone, normalizeText(request.notes()),
                request.active(), Instant.now(clock));
        customerRepository.flush();
        auditWriter.write(
                actorUserId, "CUSTOMER_UPDATED", "CUSTOMER", customerId,
                Map.of("active", customer.isActive()));
        return mapper.toCustomer(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public KhataPage<KhataResponses.LedgerEntryResponse> getStatement(
            String customerId,
            int page,
            int size) {
        validatePage(page, size);
        if (!customerRepository.existsById(customerId)) {
            throw customerNotFound();
        }
        return KhataPage.from(
                ledgerRepository.findByCustomer_IdOrderByOccurredAtDesc(
                        customerId, PageRequest.of(page, size)),
                mapper::toEntry);
    }

    @Override
    @Transactional
    public KhataResponses.SettlementResponse settle(
            String actorUserId,
            String customerId,
            String idempotencyKey,
            KhataRequests.SettlementRequest request) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        KhataLedgerEntry replay = ledgerRepository.findByIdempotencyKey(key).orElse(null);
        if (replay != null) {
            if (!replay.getCustomer().getId().equals(customerId)) {
                throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "IDEMPOTENCY_KEY_REUSED",
                        "This idempotency key belongs to another settlement.");
            }
            return mapper.toSettlement(replay, true);
        }

        BigDecimal amount = money(request.amount());
        if (request.paymentMode() == null) {
            throw invalid("INVALID_SETTLEMENT_MODE", "Settlement payment mode is required.");
        }
        if (amount.signum() <= 0) {
            throw invalid("INVALID_SETTLEMENT_AMOUNT", "Settlement amount must be greater than zero.");
        }
        CustomerCreditBalance balance = balanceRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(this::customerNotFound);
        if (balance.getVersion() != request.balanceVersion()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "STALE_KHATA_BALANCE",
                    "The customer balance has changed. Refresh and try again.");
        }
        if (amount.compareTo(balance.getOutstandingAmount()) > 0) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "SETTLEMENT_EXCEEDS_DUE",
                    "Settlement cannot exceed the outstanding amount.");
        }

        Instant now = Instant.now(clock);
        BigDecimal balanceAfter = balance.settle(amount, now);
        KhataLedgerEntry entry = KhataLedgerEntry.settlement(
                balance.getCustomer(), amount, balanceAfter, request.paymentMode(), key,
                normalizeText(request.reference()), normalizeText(request.notes()), actorUserId, now);
        ledgerRepository.save(entry);
        balanceRepository.flush();
        auditWriter.write(
                actorUserId, "KHATA_SETTLED", "CUSTOMER", customerId,
                Map.of("amount", amount, "paymentMode", request.paymentMode().name()));
        return mapper.toSettlement(entry, false);
    }

    @Override
    @Transactional(readOnly = true)
    public KhataResponses.SummaryResponse getSummary() {
        BigDecimal total = balanceRepository.totalOutstanding();
        return new KhataResponses.SummaryResponse(
                total == null ? ZERO : total.setScale(2, RoundingMode.HALF_UP),
                balanceRepository.countByOutstandingAmountGreaterThan(ZERO),
                customerRepository.countByActiveTrue());
    }

    @Override
    @Transactional(readOnly = true)
    public CreditCustomerSnapshot getCreditCustomer(String customerId) {
        Customer customer = requireCustomer(customerId);
        if (!customer.isActive()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "INACTIVE_CUSTOMER",
                    "The selected customer is inactive.");
        }
        return new CreditCustomerSnapshot(
                customer.getId(), customer.getName(), customer.getPhone(),
                customer.getCreditBalance().getOutstandingAmount());
    }

    @Override
    @Transactional
    public void postCreditSale(
            String actorUserId,
            String customerId,
            String invoiceId,
            BigDecimal requestedAmount) {
        if (ledgerRepository.existsByInvoiceId(invoiceId)) {
            return;
        }
        BigDecimal amount = money(requestedAmount);
        if (amount.signum() <= 0) {
            throw invalid("INVALID_CREDIT_AMOUNT", "Credit amount must be greater than zero.");
        }
        CustomerCreditBalance balance = balanceRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(this::customerNotFound);
        if (!balance.getCustomer().isActive()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "INACTIVE_CUSTOMER",
                    "The selected customer is inactive.");
        }
        Instant now = Instant.now(clock);
        BigDecimal balanceAfter = balance.addCredit(amount, now);
        ledgerRepository.save(KhataLedgerEntry.creditSale(
                balance.getCustomer(), amount, balanceAfter, invoiceId, actorUserId, now));
        balanceRepository.flush();
        auditWriter.write(
                actorUserId, "KHATA_CREDIT_POSTED", "CUSTOMER", customerId,
                Map.of("invoiceId", invoiceId, "amount", amount));
    }

    @Override
    @Transactional
    public void reverseCreditSale(
            String actorUserId, String customerId, String invoiceId, String saleReturnId,
            BigDecimal requestedAmount, boolean cancellation) {
        if (ledgerRepository.existsBySaleReturnId(saleReturnId)) {
            return;
        }
        BigDecimal amount = money(requestedAmount);
        CustomerCreditBalance balance = balanceRepository.findByCustomerIdForUpdate(customerId)
                .orElseThrow(this::customerNotFound);
        if (amount.signum() <= 0 || amount.compareTo(balance.getOutstandingAmount()) > 0) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT, "KHATA_REVERSAL_EXCEEDS_DUE",
                    "The Udhaar reversal cannot exceed the customer's current outstanding balance.");
        }
        Instant now = Instant.now(clock);
        BigDecimal balanceAfter = balance.settle(amount, now);
        ledgerRepository.save(KhataLedgerEntry.saleReversal(
                balance.getCustomer(), amount, balanceAfter, invoiceId, saleReturnId,
                cancellation, actorUserId, now));
        balanceRepository.flush();
        auditWriter.write(actorUserId, "KHATA_SALE_REVERSED", "CUSTOMER", customerId,
                Map.of("invoiceId", invoiceId, "saleReturnId", saleReturnId, "amount", amount));
    }

    private Customer requireCustomer(String customerId) {
        return customerRepository.findDetailedById(customerId)
                .orElseThrow(this::customerNotFound);
    }

    private void ensurePhoneAvailable(String phone, String currentCustomerId) {
        boolean exists = currentCustomerId == null
                ? customerRepository.existsByPhone(phone)
                : customerRepository.existsByPhoneAndIdNot(phone, currentCustomerId);
        if (exists) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    "CUSTOMER_PHONE_EXISTS",
                    "Another customer already uses this phone number.");
        }
    }

    private String normalizeName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isBlank()) {
            throw invalid("INVALID_CUSTOMER_NAME", "Customer name is required.");
        }
        return name;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String searchPattern(String query) {
        return query == null || query.isBlank()
                ? null
                : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
    }

    private BigDecimal money(BigDecimal value) {
        if (value == null) {
            throw invalid("INVALID_MONEY_PRECISION", "An amount is required.");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("INVALID_MONEY_PRECISION", "Amounts support at most two decimal places.");
        }
    }

    private String normalizeIdempotencyKey(String value) {
        String key = value == null ? "" : value.trim();
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw invalid(
                    "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key must contain 8 to 80 letters, numbers, dots, colons, underscores or hyphens.");
        }
        return key;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("INVALID_PAGE_REQUEST", "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private ApplicationException customerNotFound() {
        return new ApplicationException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "The customer does not exist.");
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }
}
