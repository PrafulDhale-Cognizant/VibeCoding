import { useEffect, useMemo, useState } from "react";
import { api } from "../../lib/api";
import type { ProductResponse } from "../../types";
import { ErrorNotice, Field, SelectInput, TextInput } from "../FormControls";
import { InventoryModal } from "./InventoryModal";

const code128Patterns = [
  "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312",
  "132212", "221213", "221312", "231212", "112232", "122132", "122231", "113222",
  "123122", "123221", "223211", "221132", "221231", "213212", "223112", "312131",
  "311222", "321122", "321221", "312212", "322112", "322211", "212123", "212321",
  "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
  "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121",
  "313121", "211331", "231131", "213113", "213311", "213131", "311123", "311321",
  "331121", "312113", "312311", "332111", "314111", "221411", "431111", "111224",
  "111422", "121124", "121421", "141122", "141221", "112214", "112412", "122114",
  "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
  "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112",
  "421211", "212141", "214121", "412121", "111143", "111341", "131141", "114113",
  "114311", "411113", "411311", "113141", "114131", "311141", "411131", "211412",
  "211214", "211232", "2331112"
];

type LabelSize = "38x25" | "50x30" | "58x40" | "80x50";

const labelSizes: Record<LabelSize, { width: number; height: number; label: string }> = {
  "38x25": { width: 38, height: 25, label: "38 x 25 mm" },
  "50x30": { width: 50, height: 30, label: "50 x 30 mm" },
  "58x40": { width: 58, height: 40, label: "58 x 40 mm" },
  "80x50": { width: 80, height: 50, label: "80 x 50 mm" }
};

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function formatMoney(value: number) {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2
  }).format(value);
}

export function BarcodeLabelModal({
  accessToken,
  product,
  onClose
}: {
  accessToken: string;
  product: ProductResponse;
  onClose: () => void;
}) {
  const [shopName, setShopName] = useState("Simplified Billing");
  const [labelSize, setLabelSize] = useState<LabelSize>("50x30");
  const [quantity, setQuantity] = useState("1");
  const [showShopName, setShowShopName] = useState(true);
  const [showPrice, setShowPrice] = useState(true);
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState("");
  const size = labelSizes[labelSize];
  const copies = Math.min(100, Math.max(1, Number(quantity) || 1));
  const copyIndexes = useMemo(() => Array.from({ length: copies }, (_, index) => index), [copies]);

  useEffect(() => {
    api.getStore(accessToken).then((store) => setShopName(store.shopName)).catch(() => undefined);
  }, [accessToken]);

  async function printLabels() {
    setPrinting(true);
    setError("");
    try {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve()));
      });
      if (window.billingDesktop?.printBarcodeLabels) {
        await window.billingDesktop.printBarcodeLabels({
          widthMm: size.width,
          heightMm: size.height
        });
      } else {
        window.print();
      }
    } catch (caught) {
      setError(messageFrom(caught, "The barcode labels could not be sent to the printer."));
    } finally {
      setPrinting(false);
    }
  }

  const label = (
    <BarcodeLabel
      product={product}
      shopName={showShopName ? shopName : ""}
      showPrice={showPrice}
      width={size.width}
      height={size.height}
    />
  );

  return (
    <>
      <style>{`@media print { @page { size: ${size.width}mm ${size.height}mm; margin: 0; } }`}</style>
      <InventoryModal
        title="Print barcode labels"
        description={`${product.name} - ${product.barcode}`}
        onClose={onClose}
        width="max-w-3xl"
      >
        <div className="space-y-6">
          {error && <ErrorNotice message={error} />}
          <section className="grid grid-cols-[1fr_300px] gap-6">
            <div className="space-y-4">
              <Field label="Label size">
                <SelectInput value={labelSize} onChange={(event) => setLabelSize(event.target.value as LabelSize)}>
                  {Object.entries(labelSizes).map(([value, option]) => (
                    <option key={value} value={value}>{option.label}</option>
                  ))}
                </SelectInput>
              </Field>
              <Field label="Number of labels" hint="Maximum 100 labels per print job.">
                <TextInput
                  type="number"
                  min="1"
                  max="100"
                  step="1"
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                />
              </Field>
              <div className="space-y-3 rounded-xl bg-slate-50 p-4">
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <input type="checkbox" checked={showShopName} onChange={(event) => setShowShopName(event.target.checked)} />
                  Print shop name
                </label>
                <label className="flex items-center gap-2 text-sm font-semibold text-slate-700">
                  <input type="checkbox" checked={showPrice} onChange={(event) => setShowPrice(event.target.checked)} />
                  Print selling price
                </label>
              </div>
            </div>
            <div>
              <p className="mb-2 text-xs font-bold uppercase tracking-wider text-slate-500">Label preview</p>
              <div className="flex min-h-52 items-center justify-center overflow-auto rounded-xl border border-dashed border-slate-300 bg-slate-100 p-5">
                <div className="shadow-lg">{label}</div>
              </div>
            </div>
          </section>
          <div className="flex items-center justify-between border-t border-slate-200 pt-5">
            <p className="text-sm text-slate-500">The operating-system printer dialog will open. Select your barcode or thermal printer.</p>
            <div className="flex gap-3">
              <button type="button" onClick={onClose} className="rounded-lg border border-slate-300 px-5 py-2.5 text-sm font-bold hover:bg-slate-50">Cancel</button>
              <button type="button" disabled={printing} onClick={() => void printLabels()} className="rounded-lg bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-50">
                {printing ? "Opening printer..." : `Print ${copies} label${copies === 1 ? "" : "s"}`}
              </button>
            </div>
          </div>
        </div>
      </InventoryModal>

      <div className="barcode-print-surface" aria-hidden="true">
        {copyIndexes.map((index) => (
          <div className="barcode-print-page" key={index}>{label}</div>
        ))}
      </div>
    </>
  );
}

function BarcodeLabel({
  product,
  shopName,
  showPrice,
  width,
  height
}: {
  product: ProductResponse;
  shopName: string;
  showPrice: boolean;
  width: number;
  height: number;
}) {
  const compact = width <= 38;
  return (
    <article
      className="barcode-label bg-white text-center text-black"
      style={{ width: `${width}mm`, height: `${height}mm`, padding: compact ? "1.2mm" : "1.8mm" }}
    >
      {shopName && <p className="barcode-shop truncate font-bold" style={{ fontSize: compact ? "7pt" : "8pt" }}>{shopName}</p>}
      <p className="barcode-product truncate font-bold" style={{ fontSize: compact ? "7pt" : "9pt" }}>{product.name}</p>
      <Code128Barcode value={product.barcode} height={compact ? 34 : height >= 40 ? 54 : 42} />
      <div className="flex items-center justify-between gap-2" style={{ fontSize: compact ? "6.5pt" : "8pt" }}>
        <span className="truncate font-mono">{product.barcode}</span>
        {showPrice && <strong className="shrink-0">{formatMoney(product.sellingPrice)}</strong>}
      </div>
    </article>
  );
}

function Code128Barcode({ value, height }: { value: string; height: number }) {
  const normalized = Array.from(value).map((character) => {
    const code = character.charCodeAt(0);
    return code >= 32 && code <= 126 ? character : "?";
  }).join("") || "?";
  const data = Array.from(normalized).map((character) => character.charCodeAt(0) - 32);
  const checksum = (104 + data.reduce((sum, code, index) => sum + code * (index + 1), 0)) % 103;
  const patterns = [104, ...data, checksum, 106].map((code) => code128Patterns[code]);
  const quietZone = 10;
  const moduleCount = patterns.reduce(
    (total, pattern) => total + Array.from(pattern).reduce((sum, width) => sum + Number(width), 0),
    quietZone * 2
  );
  let cursor = quietZone;
  const bars: Array<{ x: number; width: number }> = [];
  patterns.forEach((pattern) => {
    Array.from(pattern).forEach((widthValue, index) => {
      const barWidth = Number(widthValue);
      if (index % 2 === 0) bars.push({ x: cursor, width: barWidth });
      cursor += barWidth;
    });
  });

  return (
    <svg
      className="my-0.5 block w-full"
      viewBox={`0 0 ${moduleCount} ${height}`}
      height={height}
      preserveAspectRatio="none"
      role="img"
      aria-label={`Barcode ${value}`}
      shapeRendering="crispEdges"
    >
      <rect width={moduleCount} height={height} fill="white" />
      {bars.map((bar, index) => <rect key={index} x={bar.x} y="0" width={bar.width} height={height} fill="black" />)}
    </svg>
  );
}
