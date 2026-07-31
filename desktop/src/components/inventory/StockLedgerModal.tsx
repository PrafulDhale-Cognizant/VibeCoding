import { useEffect, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  ProductResponse,
  StockReasonCode,
  StockTransactionResponse
} from "../../types";
import { ErrorNotice, Field, SelectInput, SuccessNotice, TextInput } from "../FormControls";
import { InventoryModal } from "./InventoryModal";

const reasonLabels: Record<StockReasonCode, string> = {
  PHYSICAL_COUNT: "Physical count",
  DAMAGE: "Damaged stock",
  EXPIRY: "Expired stock",
  THEFT_LOSS: "Theft or loss",
  FOUND_STOCK: "Found stock",
  DATA_CORRECTION: "Data correction",
  OTHER: "Other"
};

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function formatQuantity(value: number) {
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 3 }).format(value);
}

export function StockLedgerModal({
  accessToken,
  product,
  canWrite,
  onClose,
  onAdjusted
}: {
  accessToken: string;
  product: ProductResponse;
  canWrite: boolean;
  onClose: () => void;
  onAdjusted: (updated: ProductResponse) => void;
}) {
  const [current, setCurrent] = useState(product);
  const [ledger, setLedger] = useState<StockTransactionResponse[]>([]);
  const [quantityDelta, setQuantityDelta] = useState("");
  const [reasonCode, setReasonCode] = useState<StockReasonCode>("PHYSICAL_COUNT");
  const [notes, setNotes] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  async function loadLedger() {
    setLoading(true);
    try {
      const result = await api.getStockLedger(accessToken, current.id, 0, 50);
      setLedger(result.content);
    } catch (caught) {
      setError(messageFrom(caught, "Stock ledger could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadLedger();
  }, [accessToken, current.id]);

  async function adjust(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const updated = await api.adjustStock(accessToken, current.id, {
        quantityDelta: Number(quantityDelta),
        reasonCode,
        notes,
        stockVersion: current.stockVersion
      });
      setCurrent(updated);
      setQuantityDelta("");
      setNotes("");
      setSuccess(`Stock updated to ${formatQuantity(updated.stockQuantity)}.`);
      onAdjusted(updated);
      await loadLedger();
    } catch (caught) {
      setError(messageFrom(caught, "Stock could not be adjusted."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <InventoryModal
      title={`Stock history - ${current.name}`}
      description={`Barcode ${current.barcode}`}
      onClose={onClose}
      width="max-w-4xl"
    >
      <div className="space-y-6">
        {error && <ErrorNotice message={error} />}
        {success && <SuccessNotice message={success} />}
        <section className="grid grid-cols-3 gap-4">
          <StockMetric label="Current stock" value={`${formatQuantity(current.stockQuantity)} ${current.unit.toLowerCase()}`} tone="indigo" />
          <StockMetric label="Minimum stock" value={formatQuantity(current.minimumStockLevel)} tone="slate" />
          <StockMetric label="Status" value={current.stockStatus.replaceAll("_", " ")} tone={current.stockStatus === "IN_STOCK" ? "green" : "red"} />
        </section>

        {canWrite && (
          <form onSubmit={adjust} className="rounded-xl border border-indigo-200 bg-indigo-50/60 p-5">
            <div className="flex items-start justify-between gap-4">
              <div>
                <h4 className="font-bold text-slate-950">Post stock adjustment</h4>
                <p className="mt-1 text-xs text-slate-600">Use a positive value to add stock or a negative value to remove it.</p>
              </div>
              <span className="rounded-full bg-white px-3 py-1 text-xs font-bold text-indigo-700">Audited action</span>
            </div>
            <div className="mt-4 grid grid-cols-[150px_200px_1fr_auto] items-end gap-3">
              <Field label="Quantity change">
                <TextInput
                  required
                  type="number"
                  step={["KILOGRAM", "GRAM", "LITRE", "MILLILITRE"].includes(current.unit) ? "0.001" : "1"}
                  placeholder="Example: -2"
                  value={quantityDelta}
                  onChange={(event) => setQuantityDelta(event.target.value)}
                />
              </Field>
              <Field label="Reason">
                <SelectInput value={reasonCode} onChange={(event) => setReasonCode(event.target.value as StockReasonCode)}>
                  {Object.entries(reasonLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
                </SelectInput>
              </Field>
              <Field label="Notes">
                <TextInput maxLength={500} placeholder="Optional explanation" value={notes} onChange={(event) => setNotes(event.target.value)} />
              </Field>
              <button disabled={saving || !quantityDelta || Number(quantityDelta) === 0} className="rounded-lg bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-50">
                {saving ? "Posting..." : "Post"}
              </button>
            </div>
          </form>
        )}

        <section className="overflow-hidden rounded-xl border border-slate-200">
          <header className="border-b border-slate-200 bg-slate-50 px-5 py-4">
            <h4 className="font-bold">Stock ledger</h4>
            <p className="mt-1 text-xs text-slate-500">Newest movements appear first. Ledger entries cannot be edited or deleted.</p>
          </header>
          <div className="grid grid-cols-[150px_130px_110px_110px_1fr] bg-slate-100 px-5 py-2.5 text-xs font-bold uppercase tracking-wider text-slate-500">
            <span>Date</span><span>Reason</span><span>Change</span><span>Balance</span><span>Notes</span>
          </div>
          {loading ? (
            <p className="p-5 text-sm text-slate-500">Loading stock history...</p>
          ) : ledger.length === 0 ? (
            <p className="p-5 text-sm text-slate-500">No stock movements found.</p>
          ) : (
            <div className="divide-y divide-slate-100">
              {ledger.map((entry) => (
                <div key={entry.id} className="grid grid-cols-[150px_130px_110px_110px_1fr] items-center px-5 py-3 text-sm">
                  <span className="text-xs text-slate-600">{new Date(entry.occurredAt).toLocaleString("en-IN")}</span>
                  <span className="text-xs font-semibold">{entry.reasonCode.replaceAll("_", " ")}</span>
                  <span className={`font-bold ${entry.quantityDelta > 0 ? "text-emerald-700" : "text-red-700"}`}>
                    {entry.quantityDelta > 0 ? "+" : ""}{formatQuantity(entry.quantityDelta)}
                  </span>
                  <span className="font-semibold">{formatQuantity(entry.balanceAfter)}</span>
                  <span className="truncate text-xs text-slate-500" title={entry.notes ?? ""}>{entry.notes ?? "-"}</span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </InventoryModal>
  );
}

function StockMetric({ label, value, tone }: { label: string; value: string; tone: "indigo" | "slate" | "green" | "red" }) {
  const tones = {
    indigo: "border-indigo-200 bg-indigo-50 text-indigo-900",
    slate: "border-slate-200 bg-slate-50 text-slate-900",
    green: "border-emerald-200 bg-emerald-50 text-emerald-900",
    red: "border-red-200 bg-red-50 text-red-900"
  };
  return (
    <article className={`rounded-xl border p-4 ${tones[tone]}`}>
      <p className="text-xs font-bold uppercase tracking-wider opacity-70">{label}</p>
      <p className="mt-2 text-xl font-bold capitalize">{value.toLowerCase()}</p>
    </article>
  );
}
