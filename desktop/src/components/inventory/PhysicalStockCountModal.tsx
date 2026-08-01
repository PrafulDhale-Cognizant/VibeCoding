import { useEffect, useMemo, useState } from "react";
import { api } from "../../lib/api";
import type { InventoryPage, ProductResponse } from "../../types";
import { ErrorNotice, SuccessNotice, TextInput } from "../FormControls";
import { InventoryModal } from "./InventoryModal";

const DRAFT_KEY = "simplified-billing.inventory.physical-count.v1";

function storedDraft() {
  try { return JSON.parse(localStorage.getItem(DRAFT_KEY) ?? "{}") as Record<string, number>; }
  catch { return {}; }
}

export function PhysicalStockCountModal({ accessToken, onClose, onPosted }: {
  accessToken: string; onClose: () => void; onPosted: (message: string) => void;
}) {
  const [products, setProducts] = useState<ProductResponse[]>([]); const [counts, setCounts] = useState<Record<string, number>>(storedDraft);
  const [query, setQuery] = useState(""); const [loading, setLoading] = useState(true); const [posting, setPosting] = useState(false);
  const [error, setError] = useState(""); const [success, setSuccess] = useState("");

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const all: ProductResponse[] = []; let page = 0; let result: InventoryPage<ProductResponse>;
        do { result = await api.searchProducts(accessToken, { active: true, stockStatus: "ALL", page: page++, size: 100, sort: "NAME_ASC" }); all.push(...result.content); } while (!result.last);
        if (!cancelled) setProducts(all);
      } catch (caught) { if (!cancelled) setError(caught instanceof Error ? caught.message : "Products could not be loaded."); }
      finally { if (!cancelled) setLoading(false); }
    })();
    return () => { cancelled = true; };
  }, [accessToken]);

  useEffect(() => { localStorage.setItem(DRAFT_KEY, JSON.stringify(counts)); }, [counts]);
  const visible = useMemo(() => products.filter((product) => !query.trim() || `${product.name} ${product.barcode} ${product.sku ?? ""}`.toLowerCase().includes(query.trim().toLowerCase())), [products, query]);
  const changed = products.filter((product) => counts[product.id] !== undefined && counts[product.id] !== product.stockQuantity);

  async function post() {
    if (!changed.length || !window.confirm(`Post ${changed.length} audited stock adjustments?`)) return;
    setPosting(true); setError(""); const failures: string[] = []; let completed = 0;
    for (const product of changed) {
      try { await api.adjustStock(accessToken, product.id, { quantityDelta: counts[product.id] - product.stockQuantity,
        reasonCode: "PHYSICAL_COUNT", notes: "Posted from physical stock-count draft", stockVersion: product.stockVersion }); completed++; }
      catch (caught) { failures.push(`${product.name}: ${caught instanceof Error ? caught.message : "failed"}`); }
    }
    setPosting(false);
    if (failures.length) setError(`${completed} posted. ${failures.join(" · ")}`);
    else { localStorage.removeItem(DRAFT_KEY); setCounts({}); setSuccess(`${completed} stock counts posted.`); onPosted(`${completed} stock counts posted.`); }
  }

  return <InventoryModal title="Physical stock count" description="Counts are saved locally as a draft until posted." onClose={onClose} width="max-w-5xl">
    {error && <ErrorNotice message={error} />}{success && <SuccessNotice message={success} />}
    <div className="mt-4 flex items-center gap-3"><TextInput value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search product or barcode" />
      <button disabled={posting || changed.length === 0} onClick={() => void post()} className="rounded-xl bg-indigo-700 px-5 py-3 font-bold text-white disabled:opacity-40">Post {changed.length} changes</button>
      <button disabled={posting || Object.keys(counts).length === 0} onClick={() => { if (window.confirm("Discard the saved count draft?")) setCounts({}); }} className="rounded-xl border border-slate-300 px-4 py-3 font-bold">Discard draft</button></div>
    {loading ? <p className="mt-6 text-sm text-slate-500">Loading inventory…</p> : <div className="mt-5 max-h-[55vh] overflow-auto rounded-xl border border-slate-200"><table className="w-full text-left text-sm"><thead className="sticky top-0 bg-slate-50"><tr><th className="p-3">Product</th><th>Barcode</th><th>System stock</th><th className="w-44">Counted stock</th><th>Difference</th></tr></thead><tbody>{visible.map((product) => {
      const counted = counts[product.id]; const difference = counted === undefined ? 0 : counted - product.stockQuantity;
      return <tr key={product.id} className="border-t"><td className="p-3 font-bold">{product.name}</td><td className="font-mono text-xs">{product.barcode}</td><td>{product.stockQuantity}</td><td><TextInput type="number" min={0} step={["KILOGRAM","GRAM","LITRE","MILLILITRE"].includes(product.unit) ? .001 : 1} value={counted ?? ""} placeholder="Not counted" onChange={(event) => { const raw = event.target.value; setCounts((current) => { const next = { ...current }; if (raw === "") delete next[product.id]; else next[product.id] = Number(raw); return next; }); }} /></td><td className={difference === 0 ? "text-slate-400" : difference > 0 ? "text-green-700 font-bold" : "text-red-700 font-bold"}>{difference > 0 ? "+" : ""}{difference || "—"}</td></tr>;
    })}</tbody></table></div>}
  </InventoryModal>;
}
