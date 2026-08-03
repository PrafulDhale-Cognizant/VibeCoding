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
  if (template === "ELEGANT_GOLD") {
    return <ElegantGoldInvoice invoice={invoice} logoUrl={logoUrl} duplicate={duplicate} />;
  }
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

function ElegantGoldInvoice({
  invoice,
  logoUrl,
  duplicate
}: {
  invoice: PosInvoiceResponse;
  logoUrl: string | null;
  duplicate: boolean;
}) {
  const navy = "#2d385b";
  const gold = "#d4af37";
  const peach = "#fde7ce";
  const customer = invoice.payments.find((payment) => payment.customerName);
  const quantity = invoice.totals.lines.reduce((total, line) => total + line.quantity, 0);
  const received = invoice.payments.reduce((total, payment) => total + payment.amount, 0);
  const balance = Math.max(0, invoice.totals.totalAmount - received);
  const documentTitle = invoice.totals.gstApplied ? "TAX INVOICE" : "BILL OF SUPPLY";
  const copyLabel = duplicate ? "DUPLICATE COPY" : "ORIGINAL FOR RECIPIENT";
  const initials = invoice.store.shopName.split(/\s+/).slice(0, 2).map((word) => word[0]).join("");

  return (
    <article className="a4-invoice relative flex min-h-[255mm] flex-col overflow-hidden border-2 bg-white text-sm" style={{ borderColor: gold, color: navy }}>
      <GoldCorner className="left-1 top-0" />
      <GoldCorner className="right-1 top-0 rotate-90" />
      <GoldCorner className="bottom-0 left-1 -rotate-90" />
      <GoldCorner className="bottom-0 right-1 rotate-180" />

      <header className="grid grid-cols-[120px_1fr_auto] items-center gap-6 px-7 pb-7 pt-8">
        <div className="grid h-20 w-28 place-items-center overflow-hidden">
          {logoUrl
            ? <img src={logoUrl} alt="" className="max-h-full max-w-full object-contain" />
            : <span className="grid h-16 w-16 place-items-center rounded-full border-2 text-xl font-black" style={{ borderColor: gold }}>{initials}</span>}
        </div>
        <div>
          <h1 className="font-serif text-[26px] font-black uppercase leading-tight tracking-wide" style={{ color: navy }}>{invoice.store.shopName}</h1>
          <p className="mt-4 font-semibold">☎ {invoice.store.phone}{invoice.store.gstin ? `  ·  GSTIN ${invoice.store.gstin}` : ""}</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">● {invoice.store.address}</p>
        </div>
        <div className="self-start text-right">
          <p className="text-xl font-black tracking-wide">{documentTitle}</p>
          <p className="mt-2 inline-block rounded-sm border px-3 py-1 text-[10px] font-bold tracking-wide text-slate-500" style={{ borderColor: navy }}>{copyLabel}</p>
        </div>
      </header>

      <section className="grid grid-cols-[200px_1fr] border-y px-6 py-4" style={{ borderColor: gold }}>
        <div><p className="font-bold">Invoice No.</p><p className="mt-1">{invoice.invoiceNumber}</p></div>
        <div><p className="font-bold">Invoice Date</p><p className="mt-1">{new Date(invoice.completedAt).toLocaleString("en-IN")}</p></div>
      </section>

      <section className="min-h-[30mm] border-b px-6 py-5" style={{ borderColor: gold }}>
        <p className="font-bold">Bill To</p>
        <p className="mt-1 text-base font-semibold">{customer?.customerName ?? "Walk-in customer"}</p>
        {customer?.customerPhone && <p className="mt-0.5"><strong>Mobile</strong> {customer.customerPhone}</p>}
      </section>

      <div className="flex min-h-[105mm] flex-1 flex-col">
        <table className="w-full table-fixed border-collapse">
          <thead style={{ backgroundColor: peach }}>
            <tr>
              <th className="w-14 px-4 py-3 text-center font-medium">No</th>
              <th className="px-3 py-3 text-left font-medium">Items</th>
              <th className="w-24 px-3 py-3 text-right font-medium">Qty.</th>
              <th className="w-28 px-3 py-3 text-right font-medium">Rate</th>
              <th className="w-32 px-5 py-3 text-right font-medium">Total</th>
            </tr>
          </thead>
          <tbody>
            {invoice.totals.lines.map((line) => (
              <tr key={line.lineNumber} className="align-top">
                <td className="px-4 py-4 text-center">{line.lineNumber}</td>
                <td className="px-3 py-4"><p className="font-medium">{line.name}</p><p className="mt-1 text-[10px] text-slate-500">{line.barcode}</p></td>
                <td className="px-3 py-4 text-right">{line.quantity} {line.unit}</td>
                <td className="px-3 py-4 text-right">{money.format(line.unitPrice)}</td>
                <td className="px-5 py-4 text-right">{money.format(line.lineTotal)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="mt-auto grid grid-cols-[1fr_112px_128px] items-center py-3 font-bold" style={{ backgroundColor: peach }}>
          <span className="pl-44">SUBTOTAL</span><span className="text-center">{quantity}</span><span className="pr-5 text-right">{money.format(invoice.totals.totalAmount)}</span>
        </div>
      </div>

      <footer className="grid grid-cols-2 gap-8 px-6 pb-8 pt-6">
        <div>
          <h3 className="font-semibold">Terms & Conditions</h3>
          <p className="mt-2 max-w-md text-xs leading-5 text-slate-600">{invoice.notes ?? "Goods once sold can be returned only according to the shop return policy."}</p>
          <h3 className="mt-5 font-semibold">Payment Details</h3>
          <div className="mt-2 space-y-1 text-xs text-slate-600">
            {invoice.payments.map((payment, index) => <p key={index}><strong>{payment.mode}</strong> · {money.format(payment.amount)}{payment.reference ? ` · ${payment.reference}` : ""}</p>)}
          </div>
        </div>
        <div>
          <div className="space-y-2 border-y py-3" style={{ borderColor: gold }}>
            <ElegantTotal label="Total Amount" value={invoice.totals.totalAmount} strong />
            {invoice.totals.gstApplied && (invoice.totals.taxMode === "INTRA_STATE"
              ? <><ElegantTotal label="Includes CGST" value={invoice.totals.cgstAmount} /><ElegantTotal label="Includes SGST" value={invoice.totals.sgstAmount} /></>
              : <ElegantTotal label="Includes IGST" value={invoice.totals.igstAmount} />)}
            <ElegantTotal label="Received Amount" value={received} />
            <ElegantTotal label="Balance" value={balance} strong />
          </div>
          <p className="mt-5 font-bold">Total Amount (in words)</p>
          <p className="mt-2 text-xs leading-5 text-slate-600">{amountInWords(invoice.totals.totalAmount)}</p>
        </div>
      </footer>
    </article>
  );
}

function GoldCorner({ className }: { className: string }) {
  return <span aria-hidden="true" className={`absolute text-2xl leading-none ${className}`} style={{ color: "#d4af37" }}>❦</span>;
}

function ElegantTotal({ label, value, strong = false }: { label: string; value: number; strong?: boolean }) {
  return <div className={`flex justify-between gap-4 ${strong ? "font-black" : "text-slate-600"}`}><span>{label}</span><span>{money.format(value)}</span></div>;
}

const smallNumbers = ["Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"];
const tensNumbers = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

function belowThousand(value: number): string {
  const words: string[] = [];
  if (value >= 100) {
    words.push(smallNumbers[Math.floor(value / 100)], "Hundred");
    value %= 100;
  }
  if (value >= 20) {
    words.push(tensNumbers[Math.floor(value / 10)]);
    value %= 10;
  }
  if (value > 0) words.push(smallNumbers[value]);
  return words.join(" ");
}

function integerInWords(value: number): string {
  if (value === 0) return smallNumbers[0];
  const words: string[] = [];
  const groups: Array<[number, string]> = [[10_000_000, "Crore"], [100_000, "Lakh"], [1_000, "Thousand"]];
  for (const [size, label] of groups) {
    if (value >= size) {
      const group = Math.floor(value / size);
      words.push(belowThousand(group), label);
      value %= size;
    }
  }
  if (value > 0) words.push(belowThousand(value));
  return words.join(" ");
}

function amountInWords(value: number): string {
  const rupees = Math.floor(Math.abs(value));
  const paise = Math.round((Math.abs(value) - rupees) * 100);
  return `${integerInWords(rupees)} Rupees${paise ? ` and ${integerInWords(paise)} Paise` : ""} Only`;
}

function A4Total({ label, value }: { label: string; value: number }) {
  return <div className="flex justify-between"><span>{label}</span><span>{money.format(value)}</span></div>;
}
