import { useMemo, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  CategoryResponse,
  ProductCreateRequest,
  ProductResponse,
  ProductUnit,
  ProductUpdateRequest,
  UnitResponse
} from "../../types";
import { ErrorNotice, Field, SelectInput, TextInput } from "../FormControls";
import { InventoryModal } from "./InventoryModal";

interface ProductFormState {
  name: string;
  receiptName: string;
  sku: string;
  barcode: string;
  generateBarcode: boolean;
  categoryId: string;
  unit: ProductUnit;
  hsnCode: string;
  gstRate: string;
  purchaseCost: string;
  sellingPrice: string;
  openingStock: string;
  minimumStockLevel: string;
  active: boolean;
}

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function initialForm(
  product: ProductResponse | null,
  categories: CategoryResponse[]
): ProductFormState {
  if (product) {
    return {
      name: product.name,
      receiptName: product.receiptName,
      sku: product.sku ?? "",
      barcode: product.barcode,
      generateBarcode: false,
      categoryId: product.category.id,
      unit: product.unit,
      hsnCode: product.hsnCode ?? "",
      gstRate: String(product.gstRate),
      purchaseCost: product.purchaseCost.toFixed(2),
      sellingPrice: product.sellingPrice.toFixed(2),
      openingStock: "0",
      minimumStockLevel: String(product.minimumStockLevel),
      active: product.active
    };
  }
  return {
    name: "",
    receiptName: "",
    sku: "",
    barcode: "",
    generateBarcode: false,
    categoryId: categories.find((category) => category.active)?.id ?? "",
    unit: "PIECE",
    hsnCode: "",
    gstRate: "0",
    purchaseCost: "0.00",
    sellingPrice: "0.00",
    openingStock: "0",
    minimumStockLevel: "0",
    active: true
  };
}

export function ProductEditorModal({
  accessToken,
  product,
  categories,
  units,
  onClose,
  onSaved
}: {
  accessToken: string;
  product: ProductResponse | null;
  categories: CategoryResponse[];
  units: UnitResponse[];
  onClose: () => void;
  onSaved: (product: ProductResponse, message: string) => void;
}) {
  const [form, setForm] = useState<ProductFormState>(() => initialForm(product, categories));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const selectedUnit = useMemo(
    () => units.find((unit) => unit.code === form.unit),
    [form.unit, units]
  );

  const update = <K extends keyof ProductFormState>(key: K, value: ProductFormState[K]) => {
    setForm((current) => ({ ...current, [key]: value }));
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      let saved: ProductResponse;
      if (product) {
        const body: ProductUpdateRequest = {
          name: form.name,
          receiptName: form.receiptName,
          sku: form.sku,
          barcode: form.barcode,
          categoryId: form.categoryId,
          unit: form.unit,
          hsnCode: form.hsnCode,
          gstRate: Number(form.gstRate),
          purchaseCost: Number(form.purchaseCost),
          sellingPrice: Number(form.sellingPrice),
          minimumStockLevel: Number(form.minimumStockLevel),
          active: form.active,
          version: product.version
        };
        saved = await api.updateProduct(accessToken, product.id, body);
      } else {
        const body: ProductCreateRequest = {
          name: form.name,
          receiptName: form.receiptName,
          sku: form.sku,
          barcode: form.generateBarcode ? "" : form.barcode,
          generateBarcode: form.generateBarcode,
          categoryId: form.categoryId,
          unit: form.unit,
          hsnCode: form.hsnCode,
          gstRate: Number(form.gstRate),
          purchaseCost: Number(form.purchaseCost),
          sellingPrice: Number(form.sellingPrice),
          openingStock: Number(form.openingStock),
          minimumStockLevel: Number(form.minimumStockLevel)
        };
        saved = await api.createProduct(accessToken, body);
      }
      onSaved(saved, product ? "Product updated." : `Product created with barcode ${saved.barcode}.`);
    } catch (caught) {
      setError(messageFrom(caught, "Product could not be saved."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <InventoryModal
      title={product ? "Edit product" : "Add product"}
      description="Product metadata and stock are kept separately for a complete audit trail."
      onClose={onClose}
      width="max-w-5xl"
    >
      <form onSubmit={submit} className="space-y-6">
        {error && <ErrorNotice message={error} />}
        <section>
          <h4 className="text-sm font-bold uppercase tracking-wider text-slate-500">Identity</h4>
          <div className="mt-4 grid grid-cols-2 gap-4">
            <Field label="Product name">
              <TextInput
                required
                maxLength={150}
                autoFocus
                value={form.name}
                onChange={(event) => update("name", event.target.value)}
              />
            </Field>
            <Field label="Receipt name" hint="Short name printed on thermal receipts.">
              <TextInput
                maxLength={80}
                placeholder="Defaults to product name"
                value={form.receiptName}
                onChange={(event) => update("receiptName", event.target.value)}
              />
            </Field>
            <Field label="SKU">
              <TextInput
                maxLength={64}
                value={form.sku}
                onChange={(event) => update("sku", event.target.value.toUpperCase())}
              />
            </Field>
            <Field label="Category">
              <SelectInput
                required
                value={form.categoryId}
                onChange={(event) => update("categoryId", event.target.value)}
              >
                <option value="">Select category</option>
                {categories.filter((category) => category.active).map((category) => (
                  <option key={category.id} value={category.id}>{category.name}</option>
                ))}
              </SelectInput>
            </Field>
          </div>
          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
            {!product && (
              <label className="flex items-center gap-2 text-sm font-semibold text-slate-800">
                <input
                  type="checkbox"
                  checked={form.generateBarcode}
                  onChange={(event) => update("generateBarcode", event.target.checked)}
                />
                Generate a unique internal EAN-13 barcode when saved
              </label>
            )}
            <Field
              label="Barcode"
              hint={form.generateBarcode ? "The assigned barcode will appear after saving." : "Scan or type the product barcode."}
            >
              <TextInput
                required={!form.generateBarcode}
                disabled={form.generateBarcode}
                maxLength={64}
                placeholder={form.generateBarcode ? "Generated automatically" : "Barcode"}
                value={form.generateBarcode ? "" : form.barcode}
                onChange={(event) => update("barcode", event.target.value.toUpperCase())}
              />
            </Field>
          </div>
        </section>

        <section className="border-t border-slate-200 pt-5">
          <h4 className="text-sm font-bold uppercase tracking-wider text-slate-500">Pricing and tax</h4>
          <div className="mt-4 grid grid-cols-4 gap-4">
            <Field label="Unit">
              <SelectInput value={form.unit} onChange={(event) => update("unit", event.target.value as ProductUnit)}>
                {units.map((unit) => (
                  <option key={unit.code} value={unit.code}>{unit.displayName} ({unit.symbol})</option>
                ))}
              </SelectInput>
            </Field>
            <Field label="HSN code">
              <TextInput maxLength={16} inputMode="numeric" value={form.hsnCode} onChange={(event) => update("hsnCode", event.target.value)} />
            </Field>
            <Field label="GST rate %">
              <TextInput required type="number" min="0" max="100" step="0.01" value={form.gstRate} onChange={(event) => update("gstRate", event.target.value)} />
            </Field>
            <div />
            <Field label="Purchase cost">
              <TextInput required type="number" min="0" step="0.01" value={form.purchaseCost} onChange={(event) => update("purchaseCost", event.target.value)} />
            </Field>
            <Field label="Selling price">
              <TextInput required type="number" min="0" step="0.01" value={form.sellingPrice} onChange={(event) => update("sellingPrice", event.target.value)} />
            </Field>
            <Field label="Minimum stock" hint={selectedUnit?.decimalAllowed ? "Up to 3 decimal places." : "Whole numbers only."}>
              <TextInput required type="number" min="0" step={selectedUnit?.decimalAllowed ? "0.001" : "1"} value={form.minimumStockLevel} onChange={(event) => update("minimumStockLevel", event.target.value)} />
            </Field>
            {!product && (
              <Field label="Opening stock" hint="Creates the first ledger entry.">
                <TextInput required type="number" min="0" step={selectedUnit?.decimalAllowed ? "0.001" : "1"} value={form.openingStock} onChange={(event) => update("openingStock", event.target.value)} />
              </Field>
            )}
          </div>
        </section>

        {product && (
          <section className="border-t border-slate-200 pt-5">
            <label className="flex items-center gap-2 text-sm font-semibold text-slate-800">
              <input type="checkbox" checked={form.active} onChange={(event) => update("active", event.target.checked)} />
              Product is active and available for billing
            </label>
            <p className="mt-2 text-xs text-slate-500">Stock cannot be edited here. Use the audited stock adjustment action.</p>
          </section>
        )}

        <div className="flex justify-end gap-3 border-t border-slate-200 pt-5">
          <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-5 py-2.5 text-sm font-bold hover:bg-slate-50">
            Cancel
          </button>
          <button disabled={saving || !form.categoryId} className="rounded-lg bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-50">
            {saving ? "Saving..." : product ? "Save changes" : "Create product"}
          </button>
        </div>
      </form>
    </InventoryModal>
  );
}
