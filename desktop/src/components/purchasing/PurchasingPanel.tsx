import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { api } from "../../lib/api";
import type {
  InventoryPage,
  ProductResponse,
  PurchaseResponse,
  PurchaseSummaryResponse,
  PurchasingSummaryResponse,
  SupplierBalanceStatus,
  SupplierLedgerResponse,
  SupplierPaymentMode,
  SupplierResponse
} from "../../types";
import { ErrorNotice, Field, SelectInput, SuccessNotice, TextInput } from "../FormControls";

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });
const emptyPage = <T,>(): InventoryPage<T> => ({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, first: true, last: true });
const paymentModes: Array<{ value: SupplierPaymentMode; label: string }> = [
  { value: "CASH", label: "Cash" },
  { value: "UPI", label: "UPI" },
  { value: "CARD", label: "Card" },
  { value: "BANK_TRANSFER", label: "Bank transfer" }
];

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function today() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export function PurchasingPanel({ accessToken, canPay }: { accessToken: string; canPay: boolean }) {
  const [tab, setTab] = useState<"purchases" | "suppliers">("purchases");
  const [summary, setSummary] = useState<PurchasingSummaryResponse | null>(null);
  const [suppliers, setSuppliers] = useState<InventoryPage<SupplierResponse>>(emptyPage());
  const [purchases, setPurchases] = useState<InventoryPage<PurchaseSummaryResponse>>(emptyPage());
  const [query, setQuery] = useState("");
  const [balanceStatus, setBalanceStatus] = useState<SupplierBalanceStatus>("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [supplierEditor, setSupplierEditor] = useState<SupplierResponse | "new" | null>(null);
  const [statementSupplier, setStatementSupplier] = useState<SupplierResponse | null>(null);
  const [receiving, setReceiving] = useState(false);
  const [purchaseDetail, setPurchaseDetail] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [nextSummary, nextSuppliers, nextPurchases] = await Promise.all([
        api.getPurchasingSummary(accessToken),
        api.searchSuppliers(accessToken, { query: tab === "suppliers" ? query : "", balanceStatus, page: 0, size: 50 }),
        api.searchPurchases(accessToken, { query: tab === "purchases" ? query : "", page: 0, size: 50 })
      ]);
      setSummary(nextSummary);
      setSuppliers(nextSuppliers);
      setPurchases(nextPurchases);
    } catch (caught) {
      setError(messageFrom(caught, "Purchasing data could not be loaded."));
    } finally {
      setLoading(false);
    }
  }, [accessToken, balanceStatus, query, tab]);

  useEffect(() => {
    const timer = window.setTimeout(() => void load(), 250);
    return () => window.clearTimeout(timer);
  }, [load]);

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      {error && <ErrorNotice message={error} />}
      <section className="grid grid-cols-3 gap-4">
        <Metric label="Supplier payable" value={money.format(summary?.totalOutstanding ?? 0)} tone="red" />
        <Metric label="Suppliers with due" value={String(summary?.suppliersWithDue ?? 0)} tone="amber" />
        <Metric label="Active suppliers" value={String(summary?.activeSuppliers ?? 0)} tone="indigo" />
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-center justify-between gap-5 border-b border-slate-200 p-5">
          <div className="flex rounded-full bg-slate-100 p-1">
            <TabButton selected={tab === "purchases"} onClick={() => { setTab("purchases"); setQuery(""); }}>Purchases</TabButton>
            <TabButton selected={tab === "suppliers"} onClick={() => { setTab("suppliers"); setQuery(""); }}>Suppliers</TabButton>
          </div>
          <div className="flex flex-1 justify-end gap-3">
            <TextInput
              className="mt-0 max-w-sm"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={tab === "purchases" ? "Search purchase, supplier invoice…" : "Search supplier, phone or GSTIN…"}
            />
            {tab === "suppliers" && (
              <SelectInput className="mt-0 max-w-[170px]" value={balanceStatus} onChange={(event) => setBalanceStatus(event.target.value as SupplierBalanceStatus)}>
                <option value="ALL">All balances</option><option value="DUE">With due</option><option value="CLEAR">Clear</option>
              </SelectInput>
            )}
            <button type="button" onClick={() => setSupplierEditor("new")} className="rounded-xl border border-slate-300 px-5 py-2.5 text-sm font-bold">New supplier</button>
            <button type="button" onClick={() => setReceiving(true)} className="rounded-xl bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white">Receive purchase</button>
          </div>
        </div>

        {loading ? <p className="p-8 text-sm text-slate-500">Loading purchasing workspace…</p> : tab === "purchases" ? (
          <PurchaseTable page={purchases} onOpen={(purchase) => setPurchaseDetail(purchase.id)} />
        ) : (
          <SupplierTable
            page={suppliers}
            onStatement={setStatementSupplier}
            onEdit={setSupplierEditor}
          />
        )}
      </section>

      {supplierEditor && (
        <SupplierEditorModal
          accessToken={accessToken}
          supplier={supplierEditor === "new" ? null : supplierEditor}
          onClose={() => setSupplierEditor(null)}
          onSaved={async () => { setSupplierEditor(null); await load(); }}
        />
      )}
      {statementSupplier && (
        <SupplierStatementModal
          accessToken={accessToken}
          supplier={statementSupplier}
          canPay={canPay}
          onClose={() => setStatementSupplier(null)}
          onChanged={async () => { await load(); }}
        />
      )}
      {receiving && (
        <ReceivePurchaseModal
          accessToken={accessToken}
          onClose={() => setReceiving(false)}
          onReceived={async () => { setReceiving(false); await load(); }}
        />
      )}
      {purchaseDetail && <PurchaseDetailModal accessToken={accessToken} purchaseId={purchaseDetail} onClose={() => setPurchaseDetail(null)} />}
    </div>
  );
}

function Metric({ label, value, tone }: { label: string; value: string; tone: "red" | "amber" | "indigo" }) {
  const styles = { red: "border-red-200 bg-red-50 text-red-800", amber: "border-amber-200 bg-amber-50 text-amber-900", indigo: "border-indigo-200 bg-indigo-50 text-indigo-900" };
  return <article className={`rounded-2xl border p-5 shadow-sm ${styles[tone]}`}><p className="text-xs font-bold uppercase tracking-wider opacity-70">{label}</p><p className="mt-3 text-2xl font-black">{value}</p></article>;
}

function TabButton({ selected, onClick, children }: { selected: boolean; onClick: () => void; children: ReactNode }) {
  return <button type="button" onClick={onClick} className={`rounded-full px-5 py-2 text-xs font-bold ${selected ? "bg-white text-indigo-700 shadow-sm" : "text-slate-500"}`}>{children}</button>;
}

function PurchaseTable({ page, onOpen }: { page: InventoryPage<PurchaseSummaryResponse>; onOpen: (purchase: PurchaseSummaryResponse) => void }) {
  if (page.content.length === 0) return <EmptyState title="No purchases found" copy="Receive a supplier invoice to increase stock and create purchase history." />;
  return <div><div className="grid grid-cols-[145px_1fr_150px_130px_145px_130px] bg-slate-50 px-5 py-3 text-xs font-bold uppercase tracking-wider text-slate-500"><span>Purchase</span><span>Supplier</span><span>Invoice date</span><span className="text-right">Total</span><span className="text-right">Outstanding</span><span /></div>{page.content.map((purchase) => <button key={purchase.id} type="button" onClick={() => onOpen(purchase)} className="grid w-full grid-cols-[145px_1fr_150px_130px_145px_130px] items-center border-t border-slate-100 px-5 py-4 text-left text-sm hover:bg-slate-50"><span className="font-bold text-indigo-700">{purchase.purchaseNumber}</span><span><strong className="block truncate">{purchase.supplierName}</strong><small className="text-slate-500">{purchase.supplierInvoiceNumber ?? "No supplier invoice"}</small></span><span className="text-slate-600">{purchase.invoiceDate}</span><span className="text-right font-bold">{money.format(purchase.totalAmount)}</span><span className={`text-right font-bold ${purchase.outstandingAdded > 0 ? "text-red-700" : "text-emerald-700"}`}>{money.format(purchase.outstandingAdded)}</span><span className="text-right text-xs font-bold text-indigo-700">View details</span></button>)}</div>;
}

function SupplierTable({ page, onStatement, onEdit }: { page: InventoryPage<SupplierResponse>; onStatement: (supplier: SupplierResponse) => void; onEdit: (supplier: SupplierResponse) => void }) {
  if (page.content.length === 0) return <EmptyState title="No suppliers found" copy="Create a supplier before receiving your first purchase." />;
  return <div><div className="grid grid-cols-[1fr_150px_170px_150px_190px] bg-slate-50 px-5 py-3 text-xs font-bold uppercase tracking-wider text-slate-500"><span>Supplier</span><span>Phone</span><span>GSTIN</span><span className="text-right">Outstanding</span><span /></div>{page.content.map((supplier) => <div key={supplier.id} className="grid grid-cols-[1fr_150px_170px_150px_190px] items-center border-t border-slate-100 px-5 py-4 text-sm"><span><strong className="block">{supplier.name}</strong><small className={supplier.active ? "text-emerald-700" : "text-red-700"}>{supplier.active ? "Active" : "Inactive"}</small></span><span>{supplier.phone}</span><span className="text-xs text-slate-600">{supplier.gstin ?? "—"}</span><span className={`text-right font-bold ${supplier.outstandingAmount > 0 ? "text-red-700" : "text-emerald-700"}`}>{money.format(supplier.outstandingAmount)}</span><span className="flex justify-end gap-2"><button type="button" onClick={() => onEdit(supplier)} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-bold">Edit</button><button type="button" onClick={() => onStatement(supplier)} className="rounded-lg bg-indigo-50 px-3 py-2 text-xs font-bold text-indigo-800">Statement</button></span></div>)}</div>;
}

function EmptyState({ title, copy }: { title: string; copy: string }) {
  return <div className="p-12 text-center"><p className="font-bold">{title}</p><p className="mt-2 text-sm text-slate-500">{copy}</p></div>;
}

function Modal({ title, onClose, width = "max-w-3xl", children }: { title: string; onClose: () => void; width?: string; children: ReactNode }) {
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 p-6" onMouseDown={onClose}><section className={`max-h-[94vh] w-full ${width} overflow-auto rounded-2xl bg-white shadow-2xl`} onMouseDown={(event) => event.stopPropagation()}><header className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4"><h3 className="text-lg font-bold">{title}</h3><button type="button" onClick={onClose} className="md-icon-button text-xl">×</button></header><div className="p-6">{children}</div></section></div>;
}

function SupplierEditorModal({ accessToken, supplier, onClose, onSaved }: { accessToken: string; supplier: SupplierResponse | null; onClose: () => void; onSaved: () => Promise<void> }) {
  const [form, setForm] = useState({ name: supplier?.name ?? "", phone: supplier?.phone ?? "", gstin: supplier?.gstin ?? "", address: supplier?.address ?? "", notes: supplier?.notes ?? "", active: supplier?.active ?? true });
  const [error, setError] = useState(""); const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setError(""); try { if (supplier) await api.updateSupplier(accessToken, supplier.id, { ...form, version: supplier.version }); else await api.createSupplier(accessToken, form); await onSaved(); } catch (caught) { setError(messageFrom(caught, "Supplier could not be saved.")); } finally { setSaving(false); } }
  const update = (key: keyof typeof form, value: string | boolean) => setForm((current) => ({ ...current, [key]: value }));
  return <Modal title={supplier ? "Edit supplier" : "New supplier"} onClose={onClose}><form onSubmit={submit} className="space-y-5">{error && <ErrorNotice message={error} />}<div className="grid grid-cols-2 gap-5"><Field label="Supplier name"><TextInput required maxLength={150} autoFocus value={form.name} onChange={(e) => update("name", e.target.value)} /></Field><Field label="Mobile number"><TextInput required value={form.phone} onChange={(e) => update("phone", e.target.value)} /></Field><Field label="GSTIN (optional)"><TextInput maxLength={15} value={form.gstin} onChange={(e) => update("gstin", e.target.value.toUpperCase())} /></Field><Field label="Address"><TextInput maxLength={500} value={form.address} onChange={(e) => update("address", e.target.value)} /></Field></div><Field label="Notes"><textarea className="md-input min-h-24" maxLength={500} value={form.notes} onChange={(e) => update("notes", e.target.value)} /></Field>{supplier && <label className="flex items-center gap-2 text-sm font-semibold"><input type="checkbox" checked={form.active} onChange={(e) => update("active", e.target.checked)} /> Active supplier</label>}<div className="flex justify-end gap-3"><button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-5 py-2.5 text-sm font-bold">Cancel</button><button disabled={saving} className="rounded-xl bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white disabled:opacity-50">{saving ? "Saving…" : "Save supplier"}</button></div></form></Modal>;
}

function SupplierStatementModal({ accessToken, supplier: initial, canPay, onClose, onChanged }: { accessToken: string; supplier: SupplierResponse; canPay: boolean; onClose: () => void; onChanged: () => Promise<void> }) {
  const [supplier, setSupplier] = useState(initial); const [entries, setEntries] = useState<SupplierLedgerResponse[]>([]); const [paying, setPaying] = useState(false); const [error, setError] = useState("");
  const load = useCallback(async () => { try { const [next, statement] = await Promise.all([api.getSupplier(accessToken, supplier.id), api.getSupplierStatement(accessToken, supplier.id)]); setSupplier(next); setEntries(statement.content); } catch (caught) { setError(messageFrom(caught, "Supplier statement could not be loaded.")); } }, [accessToken, supplier.id]);
  useEffect(() => { void load(); }, [load]);
  return (
    <Modal title={`${supplier.name} · Supplier statement`} onClose={onClose} width="max-w-5xl">
      <div className="space-y-5">
        {error && <ErrorNotice message={error} />}
        <div className="grid grid-cols-3 gap-4">
          <Metric label="Outstanding" value={money.format(supplier.outstandingAmount)} tone={supplier.outstandingAmount > 0 ? "red" : "indigo"} />
          <Metric label="Phone" value={supplier.phone} tone="indigo" />
          <article className="flex items-center justify-between rounded-2xl border border-indigo-200 bg-indigo-50 p-5">
            <div><p className="text-xs font-bold uppercase text-indigo-700">Supplier payment</p><p className="mt-2 text-xs text-indigo-900">Record full or partial payment</p></div>
            {canPay
              ? <button disabled={supplier.outstandingAmount <= 0} onClick={() => setPaying(true)} className="rounded-xl bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-40">Pay due</button>
              : <span className="text-xs text-slate-500">Owner/Admin only</span>}
          </article>
        </div>
        {paying && <SupplierPaymentForm accessToken={accessToken} supplier={supplier} onCancel={() => setPaying(false)} onPaid={async () => { setPaying(false); await load(); await onChanged(); }} />}
        <div className="overflow-hidden rounded-xl border border-slate-200">
          <div className="grid grid-cols-[170px_160px_140px_150px_1fr] bg-slate-50 px-4 py-3 text-xs font-bold uppercase tracking-wider text-slate-500"><span>Date</span><span>Type</span><span className="text-right">Amount</span><span className="text-right">Balance</span><span>Reference</span></div>
          {entries.length === 0
            ? <p className="p-7 text-sm text-slate-500">No payable activity yet.</p>
            : entries.map((entry) => (
              <div key={entry.id} className="grid grid-cols-[170px_160px_140px_150px_1fr] border-t border-slate-100 px-4 py-3 text-sm">
                <span className="text-xs text-slate-600">{new Date(entry.occurredAt).toLocaleString("en-IN")}</span>
                <span className={`font-bold ${entry.entryType === "PURCHASE_DUE" ? "text-red-700" : "text-emerald-700"}`}>{entry.entryType === "PURCHASE_DUE" ? "Purchase due" : "Payment"}</span>
                <span className="text-right font-bold">{money.format(entry.amount)}</span>
                <span className="text-right font-bold">{money.format(entry.balanceAfter)}</span>
                <span className="truncate text-xs text-slate-500">{entry.purchaseNumber ?? ([entry.paymentMode, entry.paymentReference, entry.notes].filter(Boolean).join(" · ") || "—")}</span>
              </div>
            ))}
        </div>
      </div>
    </Modal>
  );
}

function SupplierPaymentForm({ accessToken, supplier, onCancel, onPaid }: { accessToken: string; supplier: SupplierResponse; onCancel: () => void; onPaid: () => Promise<void> }) {
  const [amount, setAmount] = useState(supplier.outstandingAmount); const [mode, setMode] = useState<SupplierPaymentMode>("BANK_TRANSFER"); const [reference, setReference] = useState(""); const [notes, setNotes] = useState(""); const [error, setError] = useState(""); const [saving, setSaving] = useState(false); const key = useRef(crypto.randomUUID());
  async function submit(event: FormEvent) { event.preventDefault(); setSaving(true); setError(""); try { await api.paySupplier(accessToken, supplier.id, key.current, { amount, paymentMode: mode, reference, notes, balanceVersion: supplier.balanceVersion }); await onPaid(); } catch (caught) { setError(messageFrom(caught, "Supplier payment could not be recorded.")); } finally { setSaving(false); } }
  return <form onSubmit={submit} className="rounded-2xl border border-indigo-200 bg-indigo-50 p-5"><div className="grid grid-cols-[1fr_1fr_1fr_auto] items-end gap-4"><Field label="Amount"><TextInput required type="number" min="0.01" max={supplier.outstandingAmount} step="0.01" value={amount} onChange={(e) => setAmount(Number(e.target.value))} /></Field><Field label="Payment mode"><SelectInput value={mode} onChange={(e) => setMode(e.target.value as SupplierPaymentMode)}>{paymentModes.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</SelectInput></Field><Field label="Reference"><TextInput value={reference} onChange={(e) => setReference(e.target.value)} /></Field><div className="flex gap-2"><button type="button" onClick={onCancel} className="rounded-xl border border-slate-300 px-4 py-3 text-sm font-bold">Cancel</button><button disabled={saving} className="rounded-xl bg-indigo-700 px-4 py-3 text-sm font-bold text-white">Record</button></div></div><TextInput className="mt-3" placeholder="Optional notes" value={notes} onChange={(e) => setNotes(e.target.value)} />{error && <div className="mt-3"><ErrorNotice message={error} /></div>}</form>;
}

type PurchaseDraftItem = { product: ProductResponse; quantity: number; unitCost: number };

function ReceivePurchaseModal({ accessToken, onClose, onReceived }: { accessToken: string; onClose: () => void; onReceived: () => Promise<void> }) {
  const [suppliers, setSuppliers] = useState<SupplierResponse[]>([]); const [supplierId, setSupplierId] = useState(""); const [invoiceNumber, setInvoiceNumber] = useState(""); const [invoiceDate, setInvoiceDate] = useState(today()); const [includeTax, setIncludeTax] = useState(true); const [items, setItems] = useState<PurchaseDraftItem[]>([]); const [productQuery, setProductQuery] = useState(""); const [results, setResults] = useState<ProductResponse[]>([]); const [amountPaid, setAmountPaid] = useState(0); const [paymentMode, setPaymentMode] = useState<SupplierPaymentMode>("BANK_TRANSFER"); const [reference, setReference] = useState(""); const [notes, setNotes] = useState(""); const [error, setError] = useState(""); const [success, setSuccess] = useState(""); const [saving, setSaving] = useState(false); const key = useRef(crypto.randomUUID());
  useEffect(() => { api.searchSuppliers(accessToken, { active: true, size: 100 }).then((page) => { setSuppliers(page.content); if (page.content[0]) setSupplierId(page.content[0].id); }).catch((caught) => setError(messageFrom(caught, "Suppliers could not be loaded."))); }, [accessToken]);
  useEffect(() => { if (!productQuery.trim()) { setResults([]); return; } const timer = window.setTimeout(() => api.searchProducts(accessToken, { query: productQuery, active: true, size: 8 }).then((page) => setResults(page.content)).catch(() => setResults([])), 220); return () => window.clearTimeout(timer); }, [accessToken, productQuery]);
  const totals = useMemo(() => items.reduce((sum, item) => { const gross = item.quantity * item.unitCost; const taxable = includeTax ? gross * 100 / (100 + item.product.gstRate) : gross; const tax = includeTax ? gross - taxable : taxable * item.product.gstRate / 100; return { subtotal: sum.subtotal + taxable, tax: sum.tax + tax, total: sum.total + taxable + tax }; }, { subtotal: 0, tax: 0, total: 0 }), [includeTax, items]);
  function addProduct(product: ProductResponse) { setItems((current) => current.some((item) => item.product.id === product.id) ? current : [...current, { product, quantity: 1, unitCost: product.purchaseCost }]); setProductQuery(""); setResults([]); }
  function updateItem(id: string, patch: Partial<Pick<PurchaseDraftItem, "quantity" | "unitCost">>) { setItems((current) => current.map((item) => item.product.id === id ? { ...item, ...patch } : item)); }
  async function submit(event: FormEvent) { event.preventDefault(); if (!supplierId || items.length === 0) { setError("Select a supplier and add at least one product."); return; } setSaving(true); setError(""); setSuccess(""); try { const response = await api.receivePurchase(accessToken, key.current, { supplierId, supplierInvoiceNumber: invoiceNumber, invoiceDate, pricesIncludeTax: includeTax, items: items.map((item) => ({ productId: item.product.id, quantity: item.quantity, unitCost: item.unitCost })), amountPaid, paymentMode: amountPaid > 0 ? paymentMode : null, paymentReference: reference, notes }); setSuccess(`${response.purchaseNumber} received successfully.`); await onReceived(); } catch (caught) { setError(messageFrom(caught, "Purchase could not be received.")); } finally { setSaving(false); } }
  return <Modal title="Receive supplier purchase" onClose={onClose} width="max-w-6xl"><form onSubmit={submit} className="space-y-5">{error && <ErrorNotice message={error} />}{success && <SuccessNotice message={success} />}<section className="grid grid-cols-4 gap-4 rounded-2xl bg-slate-50 p-5"><Field label="Supplier"><SelectInput required value={supplierId} onChange={(e) => setSupplierId(e.target.value)}><option value="">Select supplier</option>{suppliers.map((supplier) => <option key={supplier.id} value={supplier.id}>{supplier.name}</option>)}</SelectInput></Field><Field label="Supplier invoice"><TextInput maxLength={80} value={invoiceNumber} onChange={(e) => setInvoiceNumber(e.target.value)} /></Field><Field label="Invoice date"><TextInput required type="date" value={invoiceDate} onChange={(e) => setInvoiceDate(e.target.value)} /></Field><label className="flex items-center gap-3 pt-6 text-sm font-semibold"><input type="checkbox" checked={includeTax} onChange={(e) => setIncludeTax(e.target.checked)} /> Costs include GST</label></section><section className="relative"><Field label="Add products"><TextInput value={productQuery} onChange={(e) => setProductQuery(e.target.value)} placeholder="Search name, SKU or barcode" /></Field>{results.length > 0 && <div className="absolute z-20 mt-1 max-h-64 w-full overflow-auto rounded-xl border border-slate-200 bg-white p-2 shadow-xl">{results.map((product) => <button key={product.id} type="button" onClick={() => addProduct(product)} className="flex w-full items-center justify-between rounded-lg px-3 py-3 text-left hover:bg-slate-50"><span><strong className="block text-sm">{product.name}</strong><small className="text-slate-500">{product.sku ?? product.barcode} · Stock {product.stockQuantity}</small></span><span className="text-sm font-bold">Last {money.format(product.purchaseCost)}</span></button>)}</div>}</section><section className="overflow-hidden rounded-xl border border-slate-200"><div className="grid grid-cols-[1fr_130px_150px_110px_150px_50px] bg-slate-50 px-4 py-3 text-xs font-bold uppercase text-slate-500"><span>Product</span><span>Quantity</span><span>Unit cost</span><span>GST</span><span className="text-right">Line total</span><span /></div>{items.length === 0 ? <p className="p-8 text-center text-sm text-slate-500">Search and add products to this purchase.</p> : items.map((item) => { const base = item.quantity * item.unitCost; const line = includeTax ? base : base * (1 + item.product.gstRate / 100); return <div key={item.product.id} className="grid grid-cols-[1fr_130px_150px_110px_150px_50px] items-center border-t border-slate-100 px-4 py-3"><span><strong className="block text-sm">{item.product.name}</strong><small className="text-slate-500">{item.product.unit}</small></span><TextInput className="mt-0" type="number" min="0.001" step="0.001" value={item.quantity} onChange={(e) => updateItem(item.product.id, { quantity: Number(e.target.value) })} /><TextInput className="mt-0" type="number" min="0.01" step="0.01" value={item.unitCost} onChange={(e) => updateItem(item.product.id, { unitCost: Number(e.target.value) })} /><span className="text-center text-sm">{item.product.gstRate}%</span><span className="text-right font-bold">{money.format(line)}</span><button type="button" onClick={() => setItems((current) => current.filter((lineItem) => lineItem.product.id !== item.product.id))} className="text-red-700">×</button></div>; })}</section><section className="grid grid-cols-[1fr_360px] gap-6"><div className="grid grid-cols-2 gap-4"><Field label="Amount paid now"><TextInput type="number" min="0" max={totals.total} step="0.01" value={amountPaid} onChange={(e) => setAmountPaid(Number(e.target.value))} /></Field><Field label="Payment mode"><SelectInput disabled={amountPaid <= 0} value={paymentMode} onChange={(e) => setPaymentMode(e.target.value as SupplierPaymentMode)}>{paymentModes.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}</SelectInput></Field><Field label="Payment reference"><TextInput disabled={amountPaid <= 0} value={reference} onChange={(e) => setReference(e.target.value)} /></Field><Field label="Notes"><TextInput value={notes} onChange={(e) => setNotes(e.target.value)} /></Field></div><div className="rounded-2xl bg-slate-900 p-5 text-white"><TotalRow label="Taxable" value={totals.subtotal} /><TotalRow label="GST" value={totals.tax} /><TotalRow label="Purchase total" value={totals.total} strong /><TotalRow label="Added to payable" value={Math.max(0, totals.total - amountPaid)} danger /></div></section><div className="flex justify-end gap-3"><button type="button" onClick={onClose} className="rounded-xl border border-slate-300 px-5 py-3 text-sm font-bold">Cancel</button><button disabled={saving || items.length === 0} className="rounded-xl bg-indigo-700 px-6 py-3 text-sm font-bold text-white disabled:opacity-50">{saving ? "Receiving…" : "Receive & update stock"}</button></div></form></Modal>;
}

function TotalRow({ label, value, strong, danger }: { label: string; value: number; strong?: boolean; danger?: boolean }) { return <div className={`flex justify-between py-2 ${strong ? "mt-2 border-t border-slate-700 text-lg font-black" : "text-sm"}`}><span className="opacity-70">{label}</span><span className={danger ? "font-bold text-red-200" : "font-bold"}>{money.format(value)}</span></div>; }

function PurchaseDetailModal({ accessToken, purchaseId, onClose }: { accessToken: string; purchaseId: string; onClose: () => void }) {
  const [purchase, setPurchase] = useState<PurchaseResponse | null>(null); const [error, setError] = useState("");
  useEffect(() => { api.getPurchase(accessToken, purchaseId).then(setPurchase).catch((caught) => setError(messageFrom(caught, "Purchase could not be loaded."))); }, [accessToken, purchaseId]);
  return <Modal title={purchase?.purchaseNumber ?? "Purchase details"} onClose={onClose} width="max-w-5xl">{error && <ErrorNotice message={error} />}{!purchase ? <p className="text-sm text-slate-500">Loading purchase…</p> : <div className="space-y-5"><section className="grid grid-cols-4 gap-4"><Metric label="Total" value={money.format(purchase.totalAmount)} tone="indigo" /><Metric label="Paid at receipt" value={money.format(purchase.amountPaid)} tone="indigo" /><Metric label="Payable added" value={money.format(purchase.outstandingAdded)} tone={purchase.outstandingAdded > 0 ? "red" : "indigo"} /><Metric label="GST" value={money.format(purchase.taxAmount)} tone="amber" /></section><div className="rounded-xl bg-slate-50 p-4 text-sm"><strong>{purchase.supplierName}</strong><span className="mx-2">·</span>{purchase.invoiceDate}<span className="mx-2">·</span>{purchase.supplierInvoiceNumber ?? "No supplier invoice"}</div><div className="overflow-hidden rounded-xl border border-slate-200"><div className="grid grid-cols-[1fr_110px_130px_100px_140px] bg-slate-50 px-4 py-3 text-xs font-bold uppercase text-slate-500"><span>Product</span><span className="text-right">Qty</span><span className="text-right">Cost</span><span className="text-right">GST</span><span className="text-right">Total</span></div>{purchase.items.map((item) => <div key={item.lineNumber} className="grid grid-cols-[1fr_110px_130px_100px_140px] border-t border-slate-100 px-4 py-3 text-sm"><span className="font-semibold">{item.productName}</span><span className="text-right">{item.quantity}</span><span className="text-right">{money.format(item.unitCost)}</span><span className="text-right">{item.gstRate}%</span><span className="text-right font-bold">{money.format(item.lineTotal)}</span></div>)}</div>{purchase.notes && <p className="text-sm text-slate-600">Notes: {purchase.notes}</p>}</div>}</Modal>;
}
