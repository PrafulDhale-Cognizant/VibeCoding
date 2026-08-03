import { createPortal } from "react-dom";
import type { PosInvoiceResponse } from "../../types";

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2
});

export function A4InvoicePrintSurface({
  invoice,
  logoUrl,
  duplicate
}: {
  invoice: PosInvoiceResponse;
  logoUrl: string | null;
  duplicate: boolean;
}) {
  return createPortal(
    <div className="invoice-print-portal" aria-hidden="true">
      <style>{"@media print { @page { size: A4 portrait; margin: 12mm; } }"}</style>
      <div className="report-print-surface invoice-only-print-surface">
        <A4Invoice invoice={invoice} logoUrl={logoUrl} duplicate={duplicate} />
      </div>
    </div>,
    document.body
  );
}

export function A4Invoice({
  invoice,
  logoUrl,
  duplicate
}: {
  invoice: PosInvoiceResponse;
  logoUrl: string | null;
  duplicate: boolean;
}) {
  const customer = invoice.payments.find((payment) => payment.customerName);
  const template = invoice.store.a4InvoiceTemplate ?? "MODERN";
  const palette = template === "CLASSIC"
    ? { primary: "#102a43", accent: "#d4a72c", soft: "#fff8e6", label: "Classic Premium" }
    : template === "MINIMAL"
      ? { primary: "#0f766e", accent: "#2dd4bf", soft: "#ecfdf5", label: "Minimal Fresh" }
      : { primary: "#4338ca", accent: "#38bdf8", soft: "#eef2ff", label: "Modern Color" };

  return (
    <article className="a4-invoice overflow-hidden rounded-xl border" style={{ borderColor: palette.primary }}>
      <div className="h-3" style={{ background: `linear-gradient(90deg, ${palette.primary}, ${palette.accent})` }} />
      <header className="report-print-header flex items-start justify-between p-6 text-white" style={{ backgroundColor: palette.primary }}>
        <div className="flex items-start gap-4">
          {logoUrl && (
            <span className="grid h-20 w-24 place-items-center rounded-lg bg-white p-2">
              <img src={logoUrl} alt="" className="max-h-full max-w-full object-contain" />
            </span>
          )}
          <div>
            <p className="text-xs font-bold uppercase tracking-[0.2em]" style={{ color: palette.accent }}>{palette.label}</p>
            <h1 className="mt-1 text-2xl font-black text-white">{invoice.store.shopName}</h1>
            <p className="text-white/90">{invoice.store.address}</p>
            <p className="text-white/90">Phone: {invoice.store.phone}{invoice.store.gstin ? ` · GSTIN: ${invoice.store.gstin}` : ""}</p>
          </div>
        </div>
        <div className="text-right">
          <p className="text-xs font-bold uppercase tracking-widest" style={{ color: palette.accent }}>Tax invoice</p>
          <h2 className="mt-1 text-xl font-black text-white">{invoice.invoiceNumber}</h2>
          {duplicate && <p className="mt-2 rounded border border-white px-2 py-1 text-xs font-black tracking-widest text-white">DUPLICATE COPY</p>}
        </div>
      </header>
      <div className="p-6">
        <section className="grid grid-cols-2 gap-6 rounded-lg p-4 text-sm" style={{ backgroundColor: palette.soft }}>
          <div>
            <p className="text-xs font-bold uppercase" style={{ color: palette.primary }}>Invoice details</p>
            <p className="mt-1">Date: {new Date(invoice.completedAt).toLocaleString("en-IN")}</p>
            <p>Cashier ID: {invoice.cashierUserId}</p>
            <p>Status: {invoice.status.replaceAll("_", " ")}</p>
          </div>
          <div>
            <p className="text-xs font-bold uppercase" style={{ color: palette.primary }}>Bill to</p>
            <p className="mt-1 font-bold">{customer?.customerName ?? "Walk-in customer"}</p>
            {customer?.customerPhone && <p>{customer.customerPhone}</p>}
          </div>
        </section>
        <table className="report-detail-table mt-6">
          <thead style={{ backgroundColor: palette.primary, color: "white" }}>
            <tr><th>#</th><th className="text-left">Item</th><th>Qty</th><th>Rate</th><th>Tax</th><th className="text-right">Amount</th></tr>
          </thead>
          <tbody>
            {invoice.totals.lines.map((line) => (
              <tr key={line.lineNumber}>
                <td>{line.lineNumber}</td><td>{line.name}</td><td>{line.quantity}</td>
                <td>{money.format(line.unitPrice)}</td><td>{invoice.totals.gstApplied ? `${line.gstRate}%` : "—"}</td>
                <td className="text-right">{money.format(line.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <section className="ml-auto mt-6 w-80 overflow-hidden rounded-lg border text-sm" style={{ borderColor: palette.primary }}>
          <div className="space-y-1 p-4">
            <A4Total label="Subtotal" value={invoice.totals.subtotalAmount} />
            <A4Total label="Discount" value={-(invoice.totals.lineDiscountAmount + invoice.totals.billDiscountAmount)} />
            {invoice.totals.gstApplied && (invoice.totals.taxMode === "INTRA_STATE"
              ? <><A4Total label="CGST" value={invoice.totals.cgstAmount} /><A4Total label="SGST" value={invoice.totals.sgstAmount} /></>
              : <A4Total label="IGST" value={invoice.totals.igstAmount} />)}
            <A4Total label="Round off" value={invoice.totals.roundOffAmount} />
          </div>
          <div className="flex justify-between px-4 py-3 text-lg font-black text-white" style={{ backgroundColor: palette.primary }}>
            <span>Total</span><span>{money.format(invoice.totals.totalAmount)}</span>
          </div>
        </section>
        <section className="mt-6 rounded-lg border-l-4 p-4" style={{ borderColor: palette.accent, backgroundColor: palette.soft }}>
          <h3 className="text-sm font-black" style={{ color: palette.primary }}>Payment details</h3>
          {invoice.payments.map((payment, index) => (
            <p key={index} className="text-sm">{payment.mode}: {money.format(payment.amount)}{payment.reference ? ` · Ref ${payment.reference}` : ""}</p>
          ))}
        </section>
        {invoice.notes && <p className="mt-5 text-sm"><strong>Notes:</strong> {invoice.notes}</p>}
        <footer className="report-print-footer mt-10 border-t pt-3 text-center" style={{ borderColor: palette.accent, color: palette.primary }}>
          Computer-generated invoice · Thank you for your business
        </footer>
      </div>
    </article>
  );
}

function A4Total({ label, value }: { label: string; value: number }) {
  return <div className="flex justify-between"><span>{label}</span><span>{money.format(value)}</span></div>;
}
