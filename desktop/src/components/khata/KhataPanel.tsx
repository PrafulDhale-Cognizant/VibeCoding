import { useEffect, useRef, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  InventoryPage,
  KhataBalanceStatus,
  KhataCustomerResponse,
  KhataLedgerEntryResponse,
  KhataSummaryResponse,
  SettlementMode
} from "../../types";
import { ErrorNotice, Field, SelectInput, SuccessNotice, TextInput } from "../FormControls";

const emptyPage: InventoryPage<KhataCustomerResponse> = {
  content: [], page: 0, size: 25, totalElements: 0, totalPages: 0, first: true, last: true
};

const money = new Intl.NumberFormat("en-IN", {
  style: "currency", currency: "INR", minimumFractionDigits: 2
});

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

export function KhataPanel({ accessToken }: { accessToken: string }) {
  const [customers, setCustomers] = useState<InventoryPage<KhataCustomerResponse>>(emptyPage);
  const [summary, setSummary] = useState<KhataSummaryResponse | null>(null);
  const [query, setQuery] = useState("");
  const [balanceStatus, setBalanceStatus] = useState<KhataBalanceStatus>("ALL");
  const [activeFilter, setActiveFilter] = useState<"active" | "inactive" | "all">("active");
  const [page, setPage] = useState(0);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [editing, setEditing] = useState<KhataCustomerResponse | null | undefined>(undefined);
  const [selected, setSelected] = useState<KhataCustomerResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      api.searchKhataCustomers(accessToken, {
        query,
        active: activeFilter === "all" ? null : activeFilter === "active",
        balanceStatus,
        page,
        size: 25
      }).then((result) => {
        if (!cancelled) setCustomers(result);
      }).catch((caught) => {
        if (!cancelled) setError(messageFrom(caught, "Customers could not be loaded."));
      }).finally(() => {
        if (!cancelled) setLoading(false);
      });
    }, query ? 220 : 0);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [accessToken, query, activeFilter, balanceStatus, page, refreshVersion]);

  useEffect(() => {
    api.getKhataSummary(accessToken).then(setSummary).catch(() => setSummary(null));
  }, [accessToken, refreshVersion]);

  function refresh(message?: string) {
    if (message) setSuccess(message);
    setError("");
    setRefreshVersion((value) => value + 1);
  }

  return (
    <div className="mx-auto max-w-[1500px] space-y-6">
      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}

      <section className="grid grid-cols-3 gap-5">
        <article className="rounded-2xl bg-indigo-700 p-6 text-white shadow-sm">
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-indigo-200">Total receivable</p>
          <p className="mt-3 text-4xl font-black">{money.format(summary?.totalOutstanding ?? 0)}</p>
          <p className="mt-2 text-sm text-indigo-100">Outstanding across all customer accounts</p>
        </article>
        <article className="rounded-2xl border border-red-200 bg-red-50 p-6 text-red-950 shadow-sm">
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-red-600">Customers with due</p>
          <p className="mt-3 text-4xl font-black">{summary?.customersWithDue ?? 0}</p>
          <button type="button" onClick={() => { setBalanceStatus("DUE"); setPage(0); }} className="mt-2 text-sm font-bold text-red-700">Show outstanding accounts</button>
        </article>
        <article className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-start justify-between"><div><p className="text-xs font-bold uppercase tracking-[0.18em] text-slate-500">Active customers</p><p className="mt-3 text-4xl font-black">{summary?.activeCustomers ?? 0}</p></div><button type="button" onClick={() => setEditing(null)} className="rounded-xl bg-indigo-700 px-5 py-3 text-sm font-bold text-white hover:bg-indigo-800">Add customer</button></div>
        </article>
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <header className="flex items-center justify-between gap-4 border-b border-slate-200 p-5">
          <div><h3 className="text-lg font-bold">Customer accounts</h3><p className="mt-1 text-sm text-slate-500">Search by customer name or mobile number.</p></div>
          <div className="grid w-[720px] grid-cols-[1fr_170px_150px] gap-3">
            <TextInput value={query} onChange={(event) => { setQuery(event.target.value); setPage(0); }} placeholder="Search name or phone" />
            <SelectInput value={balanceStatus} onChange={(event) => { setBalanceStatus(event.target.value as KhataBalanceStatus); setPage(0); }}><option value="ALL">All balances</option><option value="DUE">Due only</option><option value="CLEAR">Clear only</option></SelectInput>
            <SelectInput value={activeFilter} onChange={(event) => { setActiveFilter(event.target.value as typeof activeFilter); setPage(0); }}><option value="active">Active</option><option value="inactive">Inactive</option><option value="all">All status</option></SelectInput>
          </div>
        </header>

        <div className="grid grid-cols-[1.5fr_170px_180px_160px_210px] bg-slate-50 px-5 py-3 text-xs font-bold uppercase tracking-wider text-slate-500">
          <span>Customer</span><span>Phone</span><span className="text-right">Outstanding</span><span>Status</span><span className="text-right">Actions</span>
        </div>
        {loading ? <p className="p-8 text-center text-sm text-slate-500">Loading customer accounts…</p> : customers.content.length === 0 ? <p className="p-8 text-center text-sm text-slate-500">No customers match these filters.</p> : (
          <div className="divide-y divide-slate-100">
            {customers.content.map((customer) => (
              <div key={customer.id} className="grid grid-cols-[1.5fr_170px_180px_160px_210px] items-center px-5 py-4 hover:bg-slate-50">
                <div className="min-w-0"><button type="button" onClick={() => setSelected(customer)} className="truncate text-left font-bold text-indigo-800 hover:underline">{customer.name}</button><p className="mt-1 truncate text-xs text-slate-500">{customer.notes || "No notes"}</p></div>
                <span className="font-mono text-sm">{customer.phone}</span>
                <span className={`text-right text-lg font-black ${customer.outstandingAmount > 0 ? "text-red-700" : "text-emerald-700"}`}>{money.format(customer.outstandingAmount)}</span>
                <span><span className={`rounded-full px-3 py-1 text-xs font-bold ${customer.active ? "bg-emerald-50 text-emerald-800" : "bg-slate-100 text-slate-600"}`}>{customer.active ? "Active" : "Inactive"}</span></span>
                <div className="flex justify-end gap-2"><button type="button" onClick={() => setSelected(customer)} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-bold hover:bg-slate-100">Statement</button><button type="button" onClick={() => setEditing(customer)} className="rounded-lg border border-indigo-200 px-3 py-2 text-xs font-bold text-indigo-700 hover:bg-indigo-50">Edit</button></div>
              </div>
            ))}
          </div>
        )}
        <footer className="flex items-center justify-between border-t border-slate-200 px-5 py-4 text-sm text-slate-500"><span>{customers.totalElements} customer{customers.totalElements === 1 ? "" : "s"}</span><div className="flex gap-2"><button type="button" disabled={customers.first} onClick={() => setPage((value) => value - 1)} className="rounded-lg border border-slate-300 px-4 py-2 font-bold disabled:opacity-40">Previous</button><span className="px-3 py-2">Page {customers.page + 1} of {Math.max(1, customers.totalPages)}</span><button type="button" disabled={customers.last} onClick={() => setPage((value) => value + 1)} className="rounded-lg border border-slate-300 px-4 py-2 font-bold disabled:opacity-40">Next</button></div></footer>
      </section>

      {editing !== undefined && <CustomerEditor accessToken={accessToken} customer={editing} onClose={() => setEditing(undefined)} onSaved={(customer) => { setEditing(undefined); refresh(customer.version === 0 ? "Customer created." : "Customer saved."); }} />}
      {selected && <StatementModal accessToken={accessToken} customer={selected} onClose={() => setSelected(null)} onChanged={(updated) => { setSelected(updated); refresh("Khata settlement recorded."); }} />}
    </div>
  );
}

function CustomerEditor({ accessToken, customer, onClose, onSaved }: { accessToken: string; customer: KhataCustomerResponse | null; onClose: () => void; onSaved: (customer: KhataCustomerResponse) => void }) {
  const [name, setName] = useState(customer?.name ?? "");
  const [phone, setPhone] = useState(customer?.phone ?? "");
  const [notes, setNotes] = useState(customer?.notes ?? "");
  const [active, setActive] = useState(customer?.active ?? true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      const saved = customer
        ? await api.updateKhataCustomer(accessToken, customer.id, { name, phone, notes, active, version: customer.version })
        : await api.createKhataCustomer(accessToken, { name, phone, notes });
      onSaved(saved);
    } catch (caught) { setError(messageFrom(caught, "Customer could not be saved.")); }
    finally { setSaving(false); }
  }

  return <Modal title={customer ? "Edit customer" : "Add Khata customer"} onClose={onClose}><form onSubmit={submit} className="space-y-5">{error && <ErrorNotice message={error} />}<Field label="Customer name"><TextInput autoFocus required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} /></Field><Field label="Mobile number" hint="10-digit Indian mobile number"><TextInput required inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value)} /></Field><Field label="Notes"><textarea maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} className="min-h-24 w-full rounded-lg border border-slate-300 px-3 py-2 outline-none focus:border-indigo-600" /></Field>{customer && <label className="flex items-center gap-2 text-sm font-bold"><input type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} /> Active customer</label>}<div className="flex justify-end gap-3 border-t border-slate-200 pt-5"><button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-5 py-2.5 font-bold">Cancel</button><button disabled={saving} className="rounded-lg bg-indigo-700 px-5 py-2.5 font-bold text-white disabled:opacity-50">{saving ? "Saving…" : "Save customer"}</button></div></form></Modal>;
}

function StatementModal({ accessToken, customer: initial, onClose, onChanged }: { accessToken: string; customer: KhataCustomerResponse; onClose: () => void; onChanged: (customer: KhataCustomerResponse) => void }) {
  const [customer, setCustomer] = useState(initial);
  const [entries, setEntries] = useState<KhataLedgerEntryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [settling, setSettling] = useState(false);

  async function load() {
    setLoading(true);
    try {
      const [updated, statement] = await Promise.all([api.getKhataCustomer(accessToken, customer.id), api.getKhataStatement(accessToken, customer.id)]);
      setCustomer(updated); setEntries(statement.content);
    } catch (caught) { setError(messageFrom(caught, "Statement could not be loaded.")); }
    finally { setLoading(false); }
  }

  useEffect(() => { void load(); }, [accessToken, customer.id]);

  return <Modal title={`${customer.name} · Khata statement`} onClose={onClose} width="max-w-5xl"><div className="space-y-5">{error && <ErrorNotice message={error} />}<section className="grid grid-cols-[1fr_1fr_1.2fr] gap-4"><Metric label="Phone" value={customer.phone} tone="slate" /><Metric label="Outstanding" value={money.format(customer.outstandingAmount)} tone={customer.outstandingAmount > 0 ? "red" : "green"} /><div className="flex items-center justify-between rounded-xl border border-indigo-200 bg-indigo-50 p-4"><div><p className="text-xs font-bold uppercase tracking-wider text-indigo-600">Payment</p><p className="mt-2 text-sm text-indigo-900">Record full or partial settlement</p></div><button disabled={customer.outstandingAmount <= 0} onClick={() => setSettling(true)} className="rounded-lg bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-40">Settle due</button></div></section><section className="overflow-hidden rounded-xl border border-slate-200"><div className="grid grid-cols-[170px_150px_140px_160px_1fr] bg-slate-50 px-4 py-3 text-xs font-bold uppercase tracking-wider text-slate-500"><span>Date</span><span>Type</span><span className="text-right">Amount</span><span className="text-right">Balance</span><span>Reference / notes</span></div>{loading ? <p className="p-6 text-sm text-slate-500">Loading statement…</p> : entries.length === 0 ? <p className="p-6 text-sm text-slate-500">No Khata activity yet.</p> : entries.map((entry) => <div key={entry.id} className="grid grid-cols-[170px_150px_140px_160px_1fr] items-center border-t border-slate-100 px-4 py-3 text-sm"><span className="text-xs text-slate-600">{new Date(entry.occurredAt).toLocaleString("en-IN")}</span><span className={`font-bold ${entry.entryType === "CREDIT_SALE" ? "text-red-700" : "text-emerald-700"}`}>{entry.entryType === "CREDIT_SALE" ? "Udhaar sale" : "Settlement"}</span><span className={`text-right font-bold ${entry.entryType === "CREDIT_SALE" ? "text-red-700" : "text-emerald-700"}`}>{entry.entryType === "CREDIT_SALE" ? "+" : "−"}{money.format(entry.amount)}</span><span className="text-right font-bold">{money.format(entry.balanceAfter)}</span><span className="truncate text-xs text-slate-500">{entry.invoiceId ? `Invoice ${entry.invoiceId}` : [entry.paymentMode, entry.paymentReference, entry.notes].filter(Boolean).join(" · ") || "—"}</span></div>)}</section></div>{settling && <SettlementModal accessToken={accessToken} customer={customer} onClose={() => setSettling(false)} onSettled={async () => { setSettling(false); await load(); const updated = await api.getKhataCustomer(accessToken, customer.id); setCustomer(updated); onChanged(updated); }} />}</Modal>;
}

function SettlementModal({ accessToken, customer, onClose, onSettled }: { accessToken: string; customer: KhataCustomerResponse; onClose: () => void; onSettled: () => Promise<void> }) {
  const key = useRef(crypto.randomUUID());
  const [amount, setAmount] = useState(customer.outstandingAmount);
  const [paymentMode, setPaymentMode] = useState<SettlementMode>("CASH");
  const [reference, setReference] = useState("");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError("");
    try {
      await api.settleKhata(accessToken, customer.id, key.current, { amount, paymentMode, reference, notes, balanceVersion: customer.balanceVersion });
      await onSettled();
    } catch (caught) { setError(messageFrom(caught, "Settlement could not be recorded.")); }
    finally { setSaving(false); }
  }

  return <div className="fixed inset-0 z-[60] grid place-items-center bg-slate-950/75 p-6"><form onSubmit={submit} className="w-full max-w-lg rounded-2xl bg-white p-6 shadow-2xl"><div className="flex justify-between"><div><p className="text-sm font-bold text-emerald-700">Khata settlement</p><h4 className="text-xl font-bold">{customer.name}</h4></div><button type="button" onClick={onClose} className="font-bold text-slate-500">✕</button></div>{error && <div className="mt-4"><ErrorNotice message={error} /></div>}<div className="mt-5 space-y-4"><Field label="Amount" hint={`Maximum ${money.format(customer.outstandingAmount)}`}><TextInput autoFocus required type="number" min="0.01" max={customer.outstandingAmount} step="0.01" value={amount} onChange={(event) => setAmount(Number(event.target.value))} /></Field><Field label="Payment mode"><SelectInput value={paymentMode} onChange={(event) => setPaymentMode(event.target.value as SettlementMode)}><option value="CASH">Cash</option><option value="UPI">UPI</option><option value="CARD">Card</option></SelectInput></Field><Field label="Payment reference"><TextInput maxLength={100} value={reference} onChange={(event) => setReference(event.target.value)} placeholder="Optional transaction reference" /></Field><Field label="Notes"><TextInput maxLength={500} value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Optional note" /></Field></div><button disabled={saving || amount <= 0 || amount > customer.outstandingAmount} className="mt-6 w-full rounded-xl bg-emerald-600 px-5 py-3.5 font-black text-white disabled:opacity-50">{saving ? "Recording…" : `RECORD ${money.format(amount)} SETTLEMENT`}</button></form></div>;
}

function Modal({ title, onClose, width = "max-w-xl", children }: { title: string; onClose: () => void; width?: string; children: React.ReactNode }) {
  return <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/70 p-6" role="dialog" aria-modal="true"><div className={`max-h-[92vh] w-full ${width} overflow-auto rounded-2xl bg-white shadow-2xl`}><header className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-5"><h3 className="text-xl font-bold">{title}</h3><button type="button" onClick={onClose} className="rounded-lg px-3 py-2 font-bold text-slate-500 hover:bg-slate-100">✕</button></header><div className="p-6">{children}</div></div></div>;
}

function Metric({ label, value, tone }: { label: string; value: string; tone: "slate" | "red" | "green" }) {
  const colors = { slate: "border-slate-200 bg-slate-50 text-slate-900", red: "border-red-200 bg-red-50 text-red-900", green: "border-emerald-200 bg-emerald-50 text-emerald-900" };
  return <article className={`rounded-xl border p-4 ${colors[tone]}`}><p className="text-xs font-bold uppercase tracking-wider opacity-70">{label}</p><p className="mt-2 text-xl font-black">{value}</p></article>;
}
