import { useMemo, useRef, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  PaymentMode,
  ReturnDisposition,
  SaleReturnResponse,
  SaleReturnSourceInvoice
} from "../../types";
import { ErrorNotice, Field, SelectInput, SuccessNotice, TextInput } from "../FormControls";

type LineDraft = { quantity: number; disposition: ReturnDisposition };

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function money(value: number) {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(value);
}

export function SalesReturnsPanel({ accessToken }: { accessToken: string }) {
  const [invoiceNumber, setInvoiceNumber] = useState("");
  const [invoice, setInvoice] = useState<SaleReturnSourceInvoice | null>(null);
  const [lines, setLines] = useState<Record<string, LineDraft>>({});
  const [refundMode, setRefundMode] = useState<PaymentMode>("CASH");
  const [reference, setReference] = useState("");
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<SaleReturnResponse | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [loading, setLoading] = useState(false);
  const key = useRef(crypto.randomUUID());

  const selectedTotal = useMemo(() => {
    if (!invoice) return 0;
    const allRemaining = invoice.items.every((item) =>
      item.returnableQuantity === 0 || (lines[item.invoiceItemId]?.quantity ?? 0) === item.returnableQuantity
    );
    if (allRemaining) return invoice.returnableTotal;
    return invoice.items.reduce((total, item) => {
      const quantity = lines[item.invoiceItemId]?.quantity ?? 0;
      if (quantity <= 0 || item.returnableQuantity <= 0) return total;
      return total + Number((item.returnableAmount * quantity / item.returnableQuantity).toFixed(2));
    }, 0);
  }, [invoice, lines]);

  async function search(event: FormEvent) {
    event.preventDefault(); setLoading(true); setError(""); setSuccess(""); setResult(null);
    try {
      const found = await api.findSaleReturnSource(accessToken, invoiceNumber.trim());
      setInvoice(found);
      setLines(Object.fromEntries(found.items.map((item) => [item.invoiceItemId,
        { quantity: 0, disposition: "SALEABLE" as ReturnDisposition }])));
      const originalMode = found.payments[0]?.mode ?? "CASH";
      setRefundMode(originalMode);
      setReason(""); key.current = crypto.randomUUID();
    } catch (caught) {
      setInvoice(null); setError(messageFrom(caught, "Invoice could not be found."));
    } finally { setLoading(false); }
  }

  function refund(amount: number) {
    const originalCredit = invoice?.payments.find((payment) => payment.mode === "UDHAAR");
    return [{
      mode: refundMode,
      amount: Number(amount.toFixed(2)),
      reference: reference.trim() || undefined,
      customerId: refundMode === "UDHAAR" ? originalCredit?.customerId ?? undefined : undefined
    }];
  }

  async function submitReturn() {
    if (!invoice) return;
    const items = invoice.items.flatMap((item) => {
      const draft = lines[item.invoiceItemId];
      return draft?.quantity > 0 ? [{ invoiceItemId: item.invoiceItemId,
        quantity: draft.quantity, disposition: draft.disposition }] : [];
    });
    if (!items.length) { setError("Select at least one return quantity."); return; }
    setLoading(true); setError(""); setSuccess("");
    try {
      const completed = await api.returnSale(accessToken, invoice.id, key.current, {
        items, refunds: refund(selectedTotal), reason
      });
      setResult(completed); setSuccess(`Return ${completed.returnNumber} completed.`);
      setInvoice(await api.findSaleReturnSource(accessToken, invoice.invoiceNumber));
      key.current = crypto.randomUUID();
    } catch (caught) { setError(messageFrom(caught, "The sale return could not be completed.")); }
    finally { setLoading(false); }
  }

  async function cancelInvoice() {
    if (!invoice) return;
    if (!window.confirm(`Cancel invoice ${invoice.invoiceNumber} and reverse the full bill?`)) return;
    setLoading(true); setError(""); setSuccess("");
    try {
      const completed = await api.cancelSale(accessToken, invoice.id, key.current, {
        refunds: refund(invoice.totalAmount), reason
      });
      setResult(completed); setSuccess(`Invoice cancelled with credit note ${completed.returnNumber}.`);
      setInvoice(await api.findSaleReturnSource(accessToken, invoice.invoiceNumber));
      key.current = crypto.randomUUID();
    } catch (caught) { setError(messageFrom(caught, "The invoice could not be cancelled.")); }
    finally { setLoading(false); }
  }

  async function printReceipt() {
    if (window.billingDesktop?.printReport) await window.billingDesktop.printReport();
    else window.print();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-xl font-black">Sales returns & refunds</h3>
        <p className="mt-1 text-sm text-slate-500">Find the original bill, select returned quantities, then record how the customer was refunded.</p>
        <form onSubmit={search} className="mt-5 flex gap-3">
          <TextInput required autoFocus value={invoiceNumber} onChange={(event) => setInvoiceNumber(event.target.value)} placeholder="Invoice number" />
          <button disabled={loading} className="rounded-xl bg-indigo-700 px-6 py-2 font-bold text-white disabled:opacity-50">Find invoice</button>
        </form>
      </section>

      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}

      {invoice && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between">
            <div><p className="text-xs font-bold uppercase text-slate-500">Original invoice</p>
              <h3 className="mt-1 text-2xl font-black">{invoice.invoiceNumber}</h3>
              <p className="text-sm text-slate-500">{new Date(invoice.completedAt).toLocaleString("en-IN")} · {money(invoice.totalAmount)}</p></div>
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold">{invoice.status.replaceAll("_", " ")}</span>
          </div>

          <div className="mt-5 overflow-x-auto">
            <table className="w-full text-left text-sm"><thead className="border-b text-xs uppercase text-slate-500"><tr>
              <th className="py-3">Item</th><th>Sold</th><th>Available to return</th><th className="w-32">Return qty</th><th className="w-36">Condition</th><th className="text-right">Value</th>
            </tr></thead><tbody>{invoice.items.map((item) => {
              const draft = lines[item.invoiceItemId] ?? { quantity: 0, disposition: "SALEABLE" as ReturnDisposition };
              const value = item.returnableQuantity > 0 ? item.returnableAmount * draft.quantity / item.returnableQuantity : 0;
              return <tr key={item.invoiceItemId} className="border-b last:border-0"><td className="py-4 font-bold">{item.productName}</td>
                <td>{item.soldQuantity}</td><td>{item.returnableQuantity} {item.unit.toLowerCase()}</td>
                <td><TextInput type="number" min={0} max={item.returnableQuantity} step={item.unit === "KILOGRAM" || item.unit === "GRAM" || item.unit === "LITRE" || item.unit === "MILLILITRE" ? .001 : 1}
                  disabled={item.returnableQuantity <= 0} value={draft.quantity} onChange={(event) => setLines((current) => ({ ...current, [item.invoiceItemId]: { ...draft, quantity: Number(event.target.value) } }))} /></td>
                <td><SelectInput disabled={draft.quantity <= 0} value={draft.disposition} onChange={(event) => setLines((current) => ({ ...current, [item.invoiceItemId]: { ...draft, disposition: event.target.value as ReturnDisposition } }))}>
                  <option value="SALEABLE">Saleable</option><option value="DAMAGED">Damaged</option></SelectInput></td>
                <td className="text-right font-bold">{money(value)}</td></tr>;
            })}</tbody></table>
          </div>

          <div className="mt-6 grid grid-cols-3 gap-4">
            <Field label="Refund mode"><SelectInput value={refundMode} onChange={(event) => setRefundMode(event.target.value as PaymentMode)}>
              <option value="CASH">Cash</option><option value="UPI">UPI</option><option value="CARD">Card</option>
              {invoice.payments.some((payment) => payment.mode === "UDHAAR") && <option value="UDHAAR">Reverse Udhaar</option>}
            </SelectInput></Field>
            <Field label="Reference"><TextInput value={reference} onChange={(event) => setReference(event.target.value)} /></Field>
            <Field label="Mandatory reason"><TextInput required value={reason} onChange={(event) => setReason(event.target.value)} /></Field>
          </div>
          <div className="mt-6 flex items-center justify-between rounded-xl bg-slate-50 p-4">
            <div><p className="text-xs font-bold uppercase text-slate-500">Selected refund</p><p className="text-2xl font-black">{money(selectedTotal)}</p></div>
            <div className="flex gap-3">
              {invoice.status === "COMPLETED" && <button type="button" disabled={loading || !reason.trim()} onClick={() => void cancelInvoice()} className="rounded-xl border border-red-300 px-5 py-3 font-bold text-red-700 disabled:opacity-40">Cancel full invoice</button>}
              <button type="button" disabled={loading || selectedTotal <= 0 || !reason.trim()} onClick={() => void submitReturn()} className="rounded-xl bg-indigo-700 px-5 py-3 font-bold text-white disabled:opacity-40">Complete return</button>
            </div>
          </div>
        </section>
      )}

      {result && <section className="report-print-surface rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="report-print-header"><h1>{result.type === "CANCELLATION" ? "Cancellation credit note" : "Sales return receipt"}</h1>
          <h2>{result.returnNumber}</h2><p>Original invoice: {result.invoiceNumber}</p></div>
        <table className="mt-5 w-full text-sm"><thead><tr><th className="text-left">Item</th><th>Qty</th><th>Condition</th><th className="text-right">Amount</th></tr></thead>
          <tbody>{result.items.map((item) => <tr key={item.invoiceItemId}><td>{item.productName}</td><td className="text-center">{item.quantity}</td><td className="text-center">{item.disposition}</td><td className="text-right">{money(item.lineTotal)}</td></tr>)}</tbody></table>
        <div className="mt-5 text-right"><p>Tax reversed: {money(result.cgstAmount + result.sgstAmount + result.igstAmount)}</p><p className="text-xl font-black">Refund: {money(result.totalAmount)}</p></div>
        <p className="mt-4 text-sm">Reason: {result.reason}</p>
        <div className="mt-5 flex justify-end print:hidden"><button onClick={() => void printReceipt()} className="rounded-xl bg-indigo-700 px-5 py-3 font-bold text-white">Print credit note</button></div>
      </section>}
    </div>
  );
}
