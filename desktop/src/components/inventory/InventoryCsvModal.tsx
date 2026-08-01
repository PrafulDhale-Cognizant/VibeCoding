import { useState } from "react";
import { api } from "../../lib/api";
import type { CategoryResponse, ProductCreateRequest, ProductUnit, UnitResponse } from "../../types";
import { ErrorNotice, SuccessNotice } from "../FormControls";
import { InventoryModal } from "./InventoryModal";

type PreviewRow = { line: number; request: ProductCreateRequest | null; name: string; errors: string[] };
const headers = ["name", "receiptName", "sku", "barcode", "category", "unit", "hsnCode", "gstRate", "purchaseCost", "sellingPrice", "openingStock", "minimumStockLevel"];

function parseCsv(text: string) {
  const rows: string[][] = []; let row: string[] = []; let field = ""; let quoted = false;
  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (quoted && char === '"' && text[index + 1] === '"') { field += '"'; index++; }
    else if (char === '"') quoted = !quoted;
    else if (char === "," && !quoted) { row.push(field); field = ""; }
    else if ((char === "\n" || char === "\r") && !quoted) {
      if (char === "\r" && text[index + 1] === "\n") index++;
      row.push(field); if (row.some((value) => value.trim())) rows.push(row); row = []; field = "";
    } else field += char;
  }
  row.push(field); if (row.some((value) => value.trim())) rows.push(row);
  return rows;
}

export function InventoryCsvModal({ accessToken, categories, units, onClose, onImported }: {
  accessToken: string; categories: CategoryResponse[]; units: UnitResponse[];
  onClose: () => void; onImported: (message: string) => void;
}) {
  const [rows, setRows] = useState<PreviewRow[]>([]); const [error, setError] = useState("");
  const [success, setSuccess] = useState(""); const [busy, setBusy] = useState(false);

  async function load(file: File) {
    setError(""); setSuccess("");
    const parsed = parseCsv((await file.text()).replace(/^\uFEFF/, ""));
    if (!parsed.length || headers.some((header, index) => parsed[0][index]?.trim() !== header)) {
      setRows([]); setError(`CSV headers must be: ${headers.join(", ")}`); return;
    }
    const barcodeSet = new Set<string>(); const skuSet = new Set<string>();
    setRows(parsed.slice(1).map((values, rowIndex) => {
      const get = (name: string) => values[headers.indexOf(name)]?.trim() ?? "";
      const errors: string[] = []; const name = get("name");
      const category = categories.find((item) => item.name.toLowerCase() === get("category").toLowerCase());
      const unit = get("unit").toUpperCase() as ProductUnit;
      const numbers = ["gstRate", "purchaseCost", "sellingPrice", "openingStock", "minimumStockLevel"] as const;
      const numeric = Object.fromEntries(numbers.map((key) => [key, Number(get(key))])) as Record<typeof numbers[number], number>;
      if (!name) errors.push("Name is required"); if (!get("receiptName")) errors.push("Receipt name is required");
      if (!category) errors.push("Category does not exist"); if (!units.some((item) => item.code === unit)) errors.push("Invalid unit");
      numbers.forEach((key) => { if (!Number.isFinite(numeric[key]) || numeric[key] < 0) errors.push(`${key} must be a non-negative number`); });
      const barcode = get("barcode"); const sku = get("sku");
      if (barcode && barcodeSet.has(barcode)) errors.push("Duplicate barcode in file"); else if (barcode) barcodeSet.add(barcode);
      if (sku && skuSet.has(sku.toLowerCase())) errors.push("Duplicate SKU in file"); else if (sku) skuSet.add(sku.toLowerCase());
      const request: ProductCreateRequest | null = errors.length ? null : {
        name, receiptName: get("receiptName"), sku, barcode, generateBarcode: !barcode,
        categoryId: category!.id, unit, hsnCode: get("hsnCode"), gstRate: numeric.gstRate,
        purchaseCost: numeric.purchaseCost, sellingPrice: numeric.sellingPrice,
        openingStock: numeric.openingStock, minimumStockLevel: numeric.minimumStockLevel
      };
      return { line: rowIndex + 2, request, name: name || "(blank)", errors };
    }));
  }

  async function importRows() {
    const valid = rows.filter((row) => row.request); if (!valid.length) return;
    setBusy(true); setError(""); let imported = 0; const failures: string[] = [];
    for (const row of valid) {
      try { await api.createProduct(accessToken, row.request!); imported++; }
      catch (caught) { failures.push(`Line ${row.line}: ${caught instanceof Error ? caught.message : "Import failed"}`); }
    }
    setBusy(false);
    if (failures.length) setError(`${imported} imported. ${failures.join(" · ")}`);
    else { setSuccess(`${imported} products imported.`); onImported(`${imported} products imported.`); }
  }

  const validCount = rows.filter((row) => row.request).length;
  return <InventoryModal title="Import products from CSV" onClose={onClose} width="max-w-5xl">
    <p className="text-sm text-slate-500">Select a UTF-8 CSV file. Data is validated and previewed before any products are created.</p>
    <label className="mt-4 inline-block cursor-pointer rounded-lg border border-slate-300 px-4 py-2 font-bold">Choose CSV<input className="hidden" type="file" accept=".csv,text/csv" onChange={(event) => { const file = event.target.files?.[0]; if (file) void load(file); }} /></label>
    {error && <div className="mt-4"><ErrorNotice message={error} /></div>}{success && <div className="mt-4"><SuccessNotice message={success} /></div>}
    {rows.length > 0 && <><div className="mt-5 max-h-96 overflow-auto rounded-xl border border-slate-200"><table className="w-full text-left text-sm"><thead className="sticky top-0 bg-slate-50"><tr><th className="p-3">Line</th><th>Product</th><th>Status</th></tr></thead><tbody>{rows.map((row) => <tr key={row.line} className="border-t"><td className="p-3">{row.line}</td><td className="font-bold">{row.name}</td><td className={row.errors.length ? "text-red-700" : "text-green-700"}>{row.errors.length ? row.errors.join("; ") : "Ready"}</td></tr>)}</tbody></table></div>
      <div className="mt-5 flex items-center justify-between"><p className="text-sm">{validCount} valid · {rows.length - validCount} invalid</p><button disabled={busy || validCount === 0} onClick={() => void importRows()} className="rounded-xl bg-indigo-700 px-5 py-3 font-bold text-white disabled:opacity-40">{busy ? "Importing…" : `Import ${validCount} products`}</button></div></>}
  </InventoryModal>;
}
