import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { api } from "../../lib/api";
import type {
  PurchaseResponse,
  PurchaseReturnReason,
  PurchaseReturnResponse
} from "../../types";
import { ErrorNotice, Field, SelectInput, SuccessNotice, TextInput } from "../FormControls";

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });

const reasons: Array<{ value: PurchaseReturnReason; label: string }> = [
  { value: "DAMAGED", label: "Damaged goods" },
  { value: "EXPIRED", label: "Expired goods" },
  { value: "WRONG_ITEM", label: "Wrong item supplied" },
  { value: "QUALITY_ISSUE", label: "Quality issue" },
  { value: "EXCESS_STOCK", label: "Excess stock" },
  { value: "OTHER", label: "Other" }
];

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function Dialog({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-6" onMouseDown={onClose}>
      <section className="max-h-[94vh] w-full max-w-6xl overflow-auto rounded-2xl bg-white shadow-2xl" onMouseDown={(event) => event.stopPropagation()}>
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
          <div>
            <p className="text-xs font-bold uppercase tracking-wider text-amber-700">Supplier stock reversal</p>
            <h3 className="mt-1 text-lg font-bold">{title}</h3>
          </div>
          <button type="button" onClick={onClose} className="md-icon-button text-xl" aria-label="Close">×</button>
        </header>
        <div className="p-6">{children}</div>
      </section>
    </div>
  );
}

export function PurchaseReturnModal({
  accessToken,
  purchaseId,
  onClose,
  onReturned
}: {
  accessToken: string;
  purchaseId: string;
  onClose: () => void;
  onReturned: (purchaseReturnId: string) => Promise<void>;
}) {
  const [purchase, setPurchase] = useState<PurchaseResponse | null>(null);
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [returnDate, setReturnDate] = useState(today());
  const [reason, setReason] = useState<PurchaseReturnReason>("QUALITY_ISSUE");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);
  const idempotencyKey = useRef(crypto.randomUUID());

  useEffect(() => {
    api.getPurchase(accessToken, purchaseId)
      .then(setPurchase)
      .catch((caught) => setError(messageFrom(caught, "Purchase details could not be loaded.")));
  }, [accessToken, purchaseId]);

  const selectedLines = useMemo(() => purchase?.items
    .filter((line) => (quantities[line.purchaseItemId] ?? 0) > 0)
    .map((line) => ({ line, quantity: quantities[line.purchaseItemId] ?? 0 })) ?? [], [purchase, quantities]);

  const preview = useMemo(() => selectedLines.reduce((totals, selected) => {
    const gross = selected.quantity * selected.line.unitCost;
    const taxable = purchase?.pricesIncludeTax
      ? gross * 100 / (100 + selected.line.gstRate)
      : gross;
    const tax = purchase?.pricesIncludeTax
      ? gross - taxable
      : taxable * selected.line.gstRate / 100;
    return {
      subtotal: totals.subtotal + taxable,
      tax: totals.tax + tax,
      total: totals.total + taxable + tax
    };
  }, { subtotal: 0, tax: 0, total: 0 }), [purchase?.pricesIncludeTax, selectedLines]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (selectedLines.length === 0) {
      setError("Enter a return quantity for at least one purchase line.");
      return;
    }
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const response = await api.returnPurchase(
        accessToken,
        purchaseId,
        idempotencyKey.current,
        {
          returnDate,
          reason,
          items: selectedLines.map(({ line, quantity }) => ({
            purchaseItemId: line.purchaseItemId,
            quantity
          })),
          notes
        }
      );
      setSuccess(`${response.returnNumber} completed successfully.`);
      await onReturned(response.id);
    } catch (caught) {
      setError(messageFrom(caught, "Purchase return could not be completed."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Dialog title={purchase ? `Return against ${purchase.purchaseNumber}` : "Loading purchase…"} onClose={onClose}>
      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}
      {!purchase ? <p className="text-sm text-slate-500">Loading returnable purchase lines…</p> : (
        <form onSubmit={submit} className="space-y-5">
          <section className="grid grid-cols-4 gap-4 rounded-2xl bg-slate-50 p-5">
            <div><p className="text-xs font-bold uppercase text-slate-500">Supplier</p><p className="mt-2 font-bold">{purchase.supplierName}</p></div>
            <div><p className="text-xs font-bold uppercase text-slate-500">Invoice date</p><p className="mt-2 font-bold">{purchase.invoiceDate}</p></div>
            <Field label="Return date"><TextInput required type="date" min={purchase.invoiceDate} value={returnDate} onChange={(event) => setReturnDate(event.target.value)} /></Field>
            <Field label="Reason"><SelectInput value={reason} onChange={(event) => setReason(event.target.value as PurchaseReturnReason)}>{reasons.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</SelectInput></Field>
          </section>

          <section className="overflow-hidden rounded-xl border border-slate-200">
            <div className="grid grid-cols-[1fr_130px_130px_150px_150px] bg-slate-50 px-4 py-3 text-xs font-bold uppercase tracking-wider text-slate-500">
              <span>Purchase line</span><span className="text-right">Purchased</span><span className="text-right">Returned</span><span>Return now</span><span className="text-right">Estimated total</span>
            </div>
            {purchase.items.map((line) => {
              const quantity = quantities[line.purchaseItemId] ?? 0;
              const gross = quantity * line.unitCost;
              const lineTotal = purchase.pricesIncludeTax ? gross : gross * (1 + line.gstRate / 100);
              return (
                <div key={line.purchaseItemId} className="grid grid-cols-[1fr_130px_130px_150px_150px] items-center border-t border-slate-100 px-4 py-3 text-sm">
                  <span><strong className="block">{line.productName}</strong><small className="text-slate-500">{money.format(line.unitCost)} · GST {line.gstRate}% · {line.unit}</small></span>
                  <span className="text-right">{line.quantity}</span>
                  <span className="text-right text-slate-500">{line.returnedQuantity}</span>
                  <TextInput
                    className="mt-0"
                    type="number"
                    min="0"
                    max={line.returnableQuantity}
                    step="0.001"
                    disabled={line.returnableQuantity <= 0}
                    value={quantity}
                    onChange={(event) => setQuantities((current) => ({ ...current, [line.purchaseItemId]: Number(event.target.value) }))}
                  />
                  <span className="text-right font-bold">{money.format(lineTotal)}</span>
                </div>
              );
            })}
          </section>

          <section className="grid grid-cols-[1fr_360px] gap-6">
            <Field label="Return notes"><textarea className="md-input min-h-28" maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Supplier acknowledgement, transport note, or other context" /></Field>
            <div className="rounded-2xl bg-slate-900 p-5 text-white">
              <PreviewRow label="Taxable reversal" value={preview.subtotal} />
              <PreviewRow label="GST reversal" value={preview.tax} />
              <PreviewRow label="Return total" value={preview.total} strong />
              <p className="mt-3 text-xs text-slate-300">The backend recalculates exact line rounding and applies this against supplier payable first; any excess becomes supplier credit.</p>
            </div>
          </section>

          <div className="flex justify-end gap-3">
            <button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-5 py-3 text-sm font-bold">Cancel</button>
            <button disabled={saving || selectedLines.length === 0} className="rounded-xl bg-amber-600 px-6 py-3 text-sm font-bold text-white disabled:opacity-50">{saving ? "Completing return…" : "Complete return & reduce stock"}</button>
          </div>
        </form>
      )}
    </Dialog>
  );
}

function PreviewRow({ label, value, strong }: { label: string; value: number; strong?: boolean }) {
  return <div className={`flex justify-between py-2 ${strong ? "mt-2 border-t border-slate-700 text-lg font-black" : "text-sm"}`}><span className="opacity-70">{label}</span><span className="font-bold">{money.format(value)}</span></div>;
}

export function PurchaseReturnDetailModal({ accessToken, purchaseReturnId, onClose }: { accessToken: string; purchaseReturnId: string; onClose: () => void }) {
  const [purchaseReturn, setPurchaseReturn] = useState<PurchaseReturnResponse | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.getPurchaseReturn(accessToken, purchaseReturnId)
      .then(setPurchaseReturn)
      .catch((caught) => setError(messageFrom(caught, "Purchase return could not be loaded.")));
  }, [accessToken, purchaseReturnId]);

  return (
    <Dialog title={purchaseReturn?.returnNumber ?? "Purchase return details"} onClose={onClose}>
      {error && <ErrorNotice message={error} />}
      {!purchaseReturn ? <p className="text-sm text-slate-500">Loading purchase return…</p> : (
        <div className="space-y-5">
          <section className="grid grid-cols-4 gap-4">
            <DetailMetric label="Return total" value={money.format(purchaseReturn.totalAmount)} tone="amber" />
            <DetailMetric label="Payable reduced" value={money.format(purchaseReturn.payableReduction)} tone="green" />
            <DetailMetric label="Credit added" value={money.format(purchaseReturn.creditAdded)} tone="indigo" />
            <DetailMetric label="GST reversed" value={money.format(purchaseReturn.taxAmount)} tone="slate" />
          </section>
          <div className="rounded-xl bg-slate-50 p-4 text-sm"><strong>{purchaseReturn.supplierName}</strong><span className="mx-2">·</span>{purchaseReturn.purchaseNumber}<span className="mx-2">·</span>{purchaseReturn.returnDate}<span className="mx-2">·</span>{purchaseReturn.reason.replaceAll("_", " ")}</div>
          <div className="overflow-hidden rounded-xl border border-slate-200">
            <div className="grid grid-cols-[1fr_120px_130px_100px_150px] bg-slate-50 px-4 py-3 text-xs font-bold uppercase text-slate-500"><span>Product</span><span className="text-right">Quantity</span><span className="text-right">Unit cost</span><span className="text-right">GST</span><span className="text-right">Line total</span></div>
            {purchaseReturn.items.map((item) => <div key={item.purchaseItemId} className="grid grid-cols-[1fr_120px_130px_100px_150px] border-t border-slate-100 px-4 py-3 text-sm"><span className="font-semibold">{item.productName}</span><span className="text-right">{item.quantity}</span><span className="text-right">{money.format(item.unitCost)}</span><span className="text-right">{item.gstRate}%</span><span className="text-right font-bold">{money.format(item.lineTotal)}</span></div>)}
          </div>
          <section className="grid grid-cols-2 gap-4 rounded-2xl bg-slate-50 p-5 text-sm"><div><span className="text-slate-500">Supplier payable after return</span><strong className="ml-3">{money.format(purchaseReturn.supplierPayableAfter)}</strong></div><div><span className="text-slate-500">Supplier credit after return</span><strong className="ml-3 text-indigo-700">{money.format(purchaseReturn.supplierCreditAfter)}</strong></div></section>
          {purchaseReturn.notes && <p className="text-sm text-slate-600">Notes: {purchaseReturn.notes}</p>}
        </div>
      )}
    </Dialog>
  );
}

function DetailMetric({ label, value, tone }: { label: string; value: string; tone: "amber" | "green" | "indigo" | "slate" }) {
  const styles = { amber: "border-amber-200 bg-amber-50 text-amber-900", green: "border-emerald-200 bg-emerald-50 text-emerald-800", indigo: "border-indigo-200 bg-indigo-50 text-indigo-900", slate: "border-slate-200 bg-white text-slate-900" };
  return <article className={`rounded-2xl border p-5 ${styles[tone]}`}><p className="text-xs font-bold uppercase tracking-wider opacity-70">{label}</p><p className="mt-3 text-xl font-black">{value}</p></article>;
}
