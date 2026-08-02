package com.simplifiedbilling.pos.service;

import com.simplifiedbilling.inventory.service.SaleProductSnapshot;
import com.simplifiedbilling.pos.domain.DiscountType;
import com.simplifiedbilling.pos.domain.PricingLine;
import com.simplifiedbilling.pos.domain.PricingResult;
import com.simplifiedbilling.pos.domain.TaxMode;
import com.simplifiedbilling.pos.dto.PosRequests;
import com.simplifiedbilling.shared.config.PosProperties;
import com.simplifiedbilling.shared.exception.ApplicationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class PricingEngine {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal TWO = new BigDecimal("2");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final PosProperties properties;

    public PricingEngine(PosProperties properties) {
        this.properties = properties;
    }

    public PricingResult calculate(
            PosRequests.QuoteRequest request,
            List<SaleProductSnapshot> products) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw invalid("EMPTY_CART", "Add at least one item.");
        }
        if (products == null || products.size() != request.items().size()) {
            throw invalid("INVALID_CART", "Cart products could not be resolved.");
        }
        TaxMode taxMode = request.taxMode() == null ? TaxMode.INTRA_STATE : request.taxMode();
        String customerGstin = normalizeGstin(request.customerGstin());
        boolean applyGst = customerGstin != null;

        List<DraftLine> drafts = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        BigDecimal totalLineDiscount = ZERO;
        BigDecimal afterLineDiscount = ZERO;
        for (int index = 0; index < products.size(); index++) {
            SaleProductSnapshot product = products.get(index);
            PosRequests.CartItemRequest item = request.items().get(index);
            if (!product.productId().equals(item.productId())) {
                throw invalid("INVALID_CART", "Cart product order is invalid.");
            }
            BigDecimal quantity = quantity(item.quantity(), product);
            if (product.availableQuantity().compareTo(quantity) < 0) {
                throw new ApplicationException(
                        HttpStatus.CONFLICT,
                        "INSUFFICIENT_STOCK",
                        product.name() + " has only "
                                + product.availableQuantity().stripTrailingZeros().toPlainString() + " available.");
            }
            BigDecimal gross = money(product.sellingPrice().multiply(quantity));
            BigDecimal lineDiscount = discount(
                    item.discountType(), item.discountValue(), gross, "line discount");
            BigDecimal net = gross.subtract(lineDiscount);
            drafts.add(new DraftLine(index + 1, product, quantity, gross, lineDiscount, net));
            subtotal = subtotal.add(gross);
            totalLineDiscount = totalLineDiscount.add(lineDiscount);
            afterLineDiscount = afterLineDiscount.add(net);
        }

        BigDecimal billDiscount = discount(
                request.billDiscountType(),
                request.billDiscountValue(),
                afterLineDiscount,
                "bill discount");
        List<BigDecimal> billAllocations = allocate(billDiscount, drafts, afterLineDiscount);

        List<PricingLine> lines = new ArrayList<>();
        BigDecimal taxableTotal = ZERO;
        BigDecimal cgstTotal = ZERO;
        BigDecimal sgstTotal = ZERO;
        BigDecimal igstTotal = ZERO;
        BigDecimal unroundedTotal = ZERO;
        for (int index = 0; index < drafts.size(); index++) {
            DraftLine draft = drafts.get(index);
            BigDecimal allocatedBillDiscount = billAllocations.get(index);
            BigDecimal discounted = draft.net().subtract(allocatedBillDiscount);
            BigDecimal taxable;
            BigDecimal tax;
            if (!applyGst) {
                taxable = discounted;
                tax = ZERO;
            } else if (properties.pricesIncludeGst()) {
                taxable = money(discounted.multiply(ONE_HUNDRED)
                        .divide(ONE_HUNDRED.add(draft.product().gstRate()), 8, RoundingMode.HALF_UP));
                tax = discounted.subtract(taxable);
            } else {
                taxable = discounted;
                tax = money(taxable.multiply(draft.product().gstRate()).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
            }
            BigDecimal cgst = ZERO;
            BigDecimal sgst = ZERO;
            BigDecimal igst = ZERO;
            if (taxMode == TaxMode.INTER_STATE) {
                igst = tax;
            } else {
                cgst = money(tax.divide(TWO, 8, RoundingMode.HALF_UP));
                sgst = tax.subtract(cgst);
            }
            BigDecimal lineTotal = !applyGst || properties.pricesIncludeGst()
                    ? discounted
                    : taxable.add(tax);
            lineTotal = money(lineTotal);
            lines.add(new PricingLine(
                    draft.lineNumber(), draft.product(), draft.quantity(), draft.gross(),
                    draft.lineDiscount(), allocatedBillDiscount, taxable, cgst, sgst, igst, lineTotal));
            taxableTotal = taxableTotal.add(taxable);
            cgstTotal = cgstTotal.add(cgst);
            sgstTotal = sgstTotal.add(sgst);
            igstTotal = igstTotal.add(igst);
            unroundedTotal = unroundedTotal.add(lineTotal);
        }

        BigDecimal total = properties.roundPayable()
                ? unroundedTotal.setScale(0, RoundingMode.HALF_UP).setScale(2)
                : money(unroundedTotal);
        BigDecimal roundOff = total.subtract(unroundedTotal);
        return new PricingResult(
                List.copyOf(lines), taxMode, properties.pricesIncludeGst(), customerGstin, money(subtotal),
                money(totalLineDiscount), money(billDiscount), money(taxableTotal), money(cgstTotal),
                money(sgstTotal), money(igstTotal), money(roundOff), money(total));
    }

    private String normalizeGstin(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private BigDecimal quantity(BigDecimal value, SaleProductSnapshot product) {
        if (value == null || value.signum() <= 0) {
            throw invalid("INVALID_QUANTITY", "Quantity must be greater than zero.");
        }
        BigDecimal normalized;
        try {
            normalized = value.setScale(3, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid("INVALID_QUANTITY_PRECISION", "Quantities support at most three decimal places.");
        }
        if (!product.unit().isDecimalAllowed() && normalized.stripTrailingZeros().scale() > 0) {
            throw invalid("FRACTIONAL_QUANTITY_NOT_ALLOWED", product.name() + " requires a whole-number quantity.");
        }
        return normalized;
    }

    private BigDecimal discount(
            DiscountType requestedType,
            BigDecimal requestedValue,
            BigDecimal base,
            String label) {
        DiscountType type = requestedType == null ? DiscountType.NONE : requestedType;
        BigDecimal value = requestedValue == null ? ZERO : requestedValue;
        if (value.signum() < 0) {
            throw invalid("INVALID_DISCOUNT", "The " + label + " cannot be negative.");
        }
        BigDecimal amount = switch (type) {
            case NONE -> ZERO;
            case FIXED -> money(value);
            case PERCENTAGE -> {
                if (value.compareTo(ONE_HUNDRED) > 0) {
                    throw invalid("INVALID_DISCOUNT", "A percentage discount cannot exceed 100%.");
                }
                yield money(base.multiply(value).divide(ONE_HUNDRED, 8, RoundingMode.HALF_UP));
            }
        };
        if (amount.compareTo(base) > 0) {
            throw invalid("INVALID_DISCOUNT", "The " + label + " cannot exceed its amount.");
        }
        return amount;
    }

    private List<BigDecimal> allocate(
            BigDecimal totalDiscount,
            List<DraftLine> lines,
            BigDecimal allocationBase) {
        List<BigDecimal> allocations = new ArrayList<>();
        BigDecimal allocated = ZERO;
        for (int index = 0; index < lines.size(); index++) {
            BigDecimal amount;
            if (index == lines.size() - 1) {
                amount = totalDiscount.subtract(allocated);
            } else if (allocationBase.signum() == 0) {
                amount = ZERO;
            } else {
                amount = money(totalDiscount.multiply(lines.get(index).net())
                        .divide(allocationBase, 8, RoundingMode.HALF_UP));
            }
            allocations.add(amount);
            allocated = allocated.add(amount);
        }
        return allocations;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private ApplicationException invalid(String code, String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, code, message);
    }

    private record DraftLine(
            int lineNumber,
            SaleProductSnapshot product,
            BigDecimal quantity,
            BigDecimal gross,
            BigDecimal lineDiscount,
            BigDecimal net) {
    }
}
