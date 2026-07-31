package com.simplifiedbilling.purchasing.service;

import com.simplifiedbilling.inventory.service.PurchaseProductSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class PurchasePricingEngine {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    public PurchasePricingResult calculate(
            List<PurchaseProductSnapshot> products, boolean pricesIncludeTax) {
        List<PurchasePricingLine> lines = new ArrayList<>();
        BigDecimal subtotal = ZERO;
        BigDecimal taxTotal = ZERO;
        BigDecimal total = ZERO;
        for (int index = 0; index < products.size(); index++) {
            PurchaseProductSnapshot product = products.get(index);
            BigDecimal gross = product.unitCost().multiply(product.quantity())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal taxable;
            BigDecimal tax;
            BigDecimal lineTotal;
            if (pricesIncludeTax) {
                taxable = gross.multiply(HUNDRED)
                        .divide(HUNDRED.add(product.gstRate()), 2, RoundingMode.HALF_UP);
                tax = gross.subtract(taxable);
                lineTotal = gross;
            } else {
                taxable = gross;
                tax = taxable.multiply(product.gstRate())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                lineTotal = taxable.add(tax);
            }
            lines.add(new PurchasePricingLine(index + 1, product, taxable, tax, lineTotal));
            subtotal = subtotal.add(taxable);
            taxTotal = taxTotal.add(tax);
            total = total.add(lineTotal);
        }
        return new PurchasePricingResult(lines, pricesIncludeTax, subtotal, taxTotal, total);
    }
}
