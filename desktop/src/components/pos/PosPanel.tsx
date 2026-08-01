import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  DiscountType,
  KhataCustomerResponse,
  PaymentMode,
  PosCartItemRequest,
  PosInvoiceResponse,
  PosQuoteRequest,
  PosQuoteResponse,
  ProductLookupResponse,
  ProductResponse,
  TaxMode
} from "../../types";
import { ErrorNotice, SelectInput, TextInput } from "../FormControls";

interface CartLine {
  product: ProductLookupResponse;
  quantity: number;
  discountType: DiscountType;
  discountValue: number;
}

interface CartDraft {
  cart: CartLine[];
  billDiscountType: DiscountType;
  billDiscountValue: number;
  taxMode: TaxMode;
}

interface HeldCart extends CartDraft { id: string; name: string; heldAt: string; }

const ACTIVE_DRAFT_KEY = "simplified-billing.pos.active-draft.v1";
const HELD_CARTS_KEY = "simplified-billing.pos.held-carts.v1";
const PRINT_QUEUE_KEY = "simplified-billing.pos.print-queue.v1";

function loadStored<T>(key: string, fallback: T): T {
  try { return JSON.parse(localStorage.getItem(key) ?? "") as T; }
  catch { return fallback; }
}

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2
});

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function productLookup(product: ProductResponse): ProductLookupResponse {
  return {
    id: product.id,
    name: product.name,
    receiptName: product.receiptName,
    barcode: product.barcode,
    unit: product.unit,
    gstRate: product.gstRate,
    sellingPrice: product.sellingPrice,
    stockQuantity: product.stockQuantity,
    active: product.active
  };
}

export function PosPanel({ accessToken }: { accessToken: string }) {
  const recovered = useRef(loadStored<CartDraft | null>(ACTIVE_DRAFT_KEY, null)).current;
  const barcodeInput = useRef<HTMLInputElement>(null);
  const checkoutKey = useRef<string | null>(null);
  const [cart, setCart] = useState<CartLine[]>(recovered?.cart ?? []);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<ProductResponse[]>([]);
  const [searching, setSearching] = useState(false);
  const [quote, setQuote] = useState<PosQuoteResponse | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [error, setError] = useState("");
  const [billDiscountType, setBillDiscountType] = useState<DiscountType>(recovered?.billDiscountType ?? "NONE");
  const [billDiscountValue, setBillDiscountValue] = useState(recovered?.billDiscountValue ?? 0);
  const [taxMode, setTaxMode] = useState<TaxMode>(recovered?.taxMode ?? "INTRA_STATE");
  const [heldCarts, setHeldCarts] = useState<HeldCart[]>(() => loadStored(HELD_CARTS_KEY, []));
  const [printQueue, setPrintQueue] = useState<PosInvoiceResponse[]>(() => loadStored(PRINT_QUEUE_KEY, []));
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [invoice, setInvoice] = useState<PosInvoiceResponse | null>(null);

  const quoteRequest = useMemo<PosQuoteRequest>(() => ({
    items: cart.map<PosCartItemRequest>((line) => ({
      productId: line.product.id,
      quantity: line.quantity,
      discountType: line.discountType,
      discountValue: line.discountValue
    })),
    billDiscountType,
    billDiscountValue,
    taxMode
  }), [cart, billDiscountType, billDiscountValue, taxMode]);

  useEffect(() => {
    if (cart.length === 0) localStorage.removeItem(ACTIVE_DRAFT_KEY);
    else localStorage.setItem(ACTIVE_DRAFT_KEY, JSON.stringify({ cart, billDiscountType, billDiscountValue, taxMode }));
  }, [cart, billDiscountType, billDiscountValue, taxMode]);

  useEffect(() => {
    localStorage.setItem(HELD_CARTS_KEY, JSON.stringify(heldCarts));
  }, [heldCarts]);

  useEffect(() => { localStorage.setItem(PRINT_QUEUE_KEY, JSON.stringify(printQueue)); }, [printQueue]);

  useEffect(() => {
    if (cart.length === 0) {
      setQuote(null);
      setQuoting(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setQuoting(true);
      api.quoteSale(accessToken, quoteRequest)
        .then((value) => {
          if (!cancelled) {
            setQuote(value);
            setError("");
          }
        })
        .catch((caught) => {
          if (!cancelled) {
            setQuote(null);
            setError(messageFrom(caught, "The bill could not be calculated."));
          }
        })
        .finally(() => {
          if (!cancelled) setQuoting(false);
        });
    }, 180);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [accessToken, cart.length, quoteRequest]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setSearching(true);
      api.searchProducts(accessToken, {
        query,
        active: true,
        stockStatus: "ALL",
        page: 0,
        size: 8,
        sort: "NAME_ASC"
      }).then((page) => {
        if (!cancelled) setResults(page.content);
      }).catch(() => {
        if (!cancelled) setResults([]);
      }).finally(() => {
        if (!cancelled) setSearching(false);
      });
    }, 220);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [accessToken, query]);

  useEffect(() => {
    function onShortcut(event: globalThis.KeyboardEvent) {
      if (event.key === "F1") {
        event.preventDefault();
        barcodeInput.current?.focus();
        barcodeInput.current?.select();
      } else if (event.key === "F2") {
        event.preventDefault();
        if (quote) setPaymentOpen(true);
      } else if (event.key === "F4") {
        event.preventDefault();
        if (invoice) void printReceipt(invoice);
        else if (quote) setPaymentOpen(true);
      } else if (event.key === "Escape" && !paymentOpen && !invoice && cart.length > 0) {
        const tag = (event.target as HTMLElement | null)?.tagName;
        if (tag !== "SELECT" && window.confirm("Clear the current cart?")) clearCart();
      }
    }
    window.addEventListener("keydown", onShortcut);
    return () => window.removeEventListener("keydown", onShortcut);
  }, [cart.length, invoice, paymentOpen, quote]);

  useEffect(() => {
    if (!paymentOpen && !invoice) barcodeInput.current?.focus();
  }, [paymentOpen, invoice]);

  function mutateCart(updater: (current: CartLine[]) => CartLine[]) {
    checkoutKey.current = null;
    setInvoice(null);
    setCart(updater);
  }

  function addProduct(product: ProductLookupResponse) {
    if (!product.active) {
      setError(`${product.name} is inactive.`);
      return;
    }
    if (product.stockQuantity <= 0) {
      setError(`${product.name} is out of stock.`);
      return;
    }
    mutateCart((current) => {
      const existing = current.find((line) => line.product.id === product.id);
      if (existing) {
        if (existing.quantity + 1 > product.stockQuantity) {
          setError(`Only ${product.stockQuantity} ${product.unit.toLowerCase()} available.`);
          return current;
        }
        return current.map((line) => line.product.id === product.id
          ? { ...line, quantity: line.quantity + 1, product }
          : line);
      }
      return [...current, { product, quantity: 1, discountType: "NONE", discountValue: 0 }];
    });
    setQuery("");
    setResults([]);
    setError("");
    window.setTimeout(() => barcodeInput.current?.focus(), 0);
  }

  async function scan(event: FormEvent) {
    event.preventDefault();
    const barcode = query.trim();
    if (!barcode) return;
    setSearching(true);
    setError("");
    try {
      addProduct(await api.findProductByBarcode(accessToken, barcode));
    } catch (caught) {
      setError(messageFrom(caught, `No product was found for barcode ${barcode}.`));
      barcodeInput.current?.select();
    } finally {
      setSearching(false);
    }
  }

  function updateQuantity(productId: string, value: number) {
    mutateCart((current) => current.map((line) => {
      if (line.product.id !== productId) return line;
      const quantity = Math.max(0, Math.min(value, line.product.stockQuantity));
      return { ...line, quantity };
    }).filter((line) => line.quantity > 0));
  }

  function updateDiscount(productId: string, type: DiscountType, value: number) {
    mutateCart((current) => current.map((line) => line.product.id === productId
      ? { ...line, discountType: type, discountValue: Math.max(0, value) }
      : line));
  }

  function clearCart() {
    checkoutKey.current = null;
    setCart([]);
    setQuote(null);
    setError("");
    setBillDiscountType("NONE");
    setBillDiscountValue(0);
  }

  function holdCart() {
    if (cart.length === 0) return;
    const name = window.prompt("Name this held cart", `Cart ${heldCarts.length + 1}`)?.trim();
    if (!name) return;
    setHeldCarts((current) => [...current, {
      id: crypto.randomUUID(), name, heldAt: new Date().toISOString(),
      cart, billDiscountType, billDiscountValue, taxMode
    }]);
    clearCart();
  }

  function resumeCart(held: HeldCart) {
    if (cart.length > 0 && !window.confirm("Replace the active cart with this held cart?")) return;
    setCart(held.cart); setBillDiscountType(held.billDiscountType);
    setBillDiscountValue(held.billDiscountValue); setTaxMode(held.taxMode);
    setHeldCarts((current) => current.filter((item) => item.id !== held.id));
    checkoutKey.current = null; setInvoice(null);
  }

  async function printReceipt(receipt: PosInvoiceResponse) {
    try {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve()));
      });
      const widthMm = receipt.store.receiptWidth === "MM_58" ? 58 : 80;
      if (window.billingDesktop?.printReceipt) {
        await window.billingDesktop.printReceipt({ widthMm });
      } else {
        window.print();
      }
      setPrintQueue((current) => current.filter((item) => item.id !== receipt.id));
    } catch (caught) {
      setError(messageFrom(caught, "The receipt could not be printed."));
      setPrintQueue((current) => current.some((item) => item.id === receipt.id) ? current : [...current, receipt]);
    }
  }

  return (
    <div className="mx-auto max-w-[1700px]">
      {error && <div className="mb-4"><ErrorNotice message={error} /></div>}
      <div className="grid min-h-[calc(100vh-150px)] grid-cols-[minmax(0,7fr)_minmax(340px,3fr)] gap-5">
        <section className="flex min-w-0 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          <div className="flex items-center justify-between border-b border-slate-200 px-5 py-4">
            <div>
              <p className="text-xs font-bold uppercase tracking-[0.18em] text-indigo-600">Active invoice</p>
              <h3 className="mt-1 text-xl font-bold">{cart.length} product{cart.length === 1 ? "" : "s"}</h3>
            </div>
            <div className="flex items-center gap-2 text-xs font-bold text-slate-500">
              {cart.length > 0 && <button type="button" onClick={holdCart} className="rounded-lg border border-slate-300 px-3 py-2 text-indigo-700">Hold cart</button>}
              <Shortcut label="F1" text="Scan" />
              <Shortcut label="F2" text="Pay" />
              <Shortcut label="F4" text="Save/Print" />
              <Shortcut label="ESC" text="Clear" />
            </div>
          </div>

          {heldCarts.length > 0 && <div className="flex items-center gap-2 border-b border-slate-200 bg-amber-50 px-5 py-2 text-xs">
            <strong className="text-amber-900">Held:</strong>
            {heldCarts.map((held) => <button key={held.id} type="button" onClick={() => resumeCart(held)} className="rounded-full bg-white px-3 py-1 font-bold text-amber-900 shadow-sm" title={new Date(held.heldAt).toLocaleString("en-IN")}>{held.name} · {held.cart.length}</button>)}
          </div>}
          {printQueue.length > 0 && <div className="flex items-center gap-2 border-b border-red-200 bg-red-50 px-5 py-2 text-xs">
            <strong className="text-red-900">Print retry:</strong>{printQueue.map((queued) => <button key={queued.id} type="button" onClick={() => setInvoice(queued)} className="rounded-full bg-white px-3 py-1 font-bold text-red-800 shadow-sm">{queued.invoiceNumber}</button>)}
          </div>}

          {cart.length === 0 ? (
            <div className="grid flex-1 place-items-center px-6 text-center">
              <div>
                <div className="mx-auto grid h-16 w-16 place-items-center rounded-2xl bg-indigo-50 text-3xl">▥</div>
                <h4 className="mt-5 text-xl font-bold">Ready for the first scan</h4>
                <p className="mt-2 text-sm text-slate-500">Scan a barcode or search for a product on the right.</p>
              </div>
            </div>
          ) : (
            <div className="min-h-0 flex-1 overflow-auto">
              <table className="w-full table-fixed text-left">
                <thead className="sticky top-0 z-10 bg-slate-50 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="w-[34%] px-5 py-3">Item</th>
                    <th className="w-[19%] px-3 py-3">Quantity</th>
                    <th className="w-[14%] px-3 py-3 text-right">Price</th>
                    <th className="w-[19%] px-3 py-3">Discount</th>
                    <th className="w-[14%] px-5 py-3 text-right">Total</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {cart.map((line, index) => {
                    const calculated = quote?.lines.find((item) => item.productId === line.product.id);
                    const step = ["KILOGRAM", "GRAM", "LITRE", "MILLILITRE"].includes(line.product.unit) ? 0.001 : 1;
                    return (
                      <tr key={line.product.id} className="align-top hover:bg-slate-50/70">
                        <td className="px-5 py-4">
                          <div className="flex gap-3">
                            <span className="mt-0.5 grid h-7 w-7 shrink-0 place-items-center rounded-full bg-indigo-50 text-xs font-bold text-indigo-700">{index + 1}</span>
                            <div className="min-w-0">
                              <p className="truncate font-bold text-slate-900">{line.product.name}</p>
                              <p className="mt-1 truncate font-mono text-xs text-slate-500">{line.product.barcode} · GST {line.product.gstRate}%</p>
                              <button type="button" onClick={() => mutateCart((current) => current.filter((item) => item.product.id !== line.product.id))} className="mt-2 text-xs font-bold text-red-600 hover:text-red-700">Remove</button>
                            </div>
                          </div>
                        </td>
                        <td className="px-3 py-4">
                          <div className="flex items-center overflow-hidden rounded-lg border border-slate-300 bg-white">
                            <button type="button" onClick={() => updateQuantity(line.product.id, line.quantity - step)} className="px-3 py-2 font-bold hover:bg-slate-100">−</button>
                            <input aria-label={`Quantity for ${line.product.name}`} className="min-w-0 flex-1 border-x border-slate-200 px-1 py-2 text-center text-sm font-bold outline-none" type="number" min={step} max={line.product.stockQuantity} step={step} value={line.quantity} onChange={(event) => updateQuantity(line.product.id, Number(event.target.value))} />
                            <button type="button" onClick={() => updateQuantity(line.product.id, line.quantity + step)} className="px-3 py-2 font-bold hover:bg-slate-100">+</button>
                          </div>
                          <p className="mt-1 text-center text-[11px] text-slate-400">{line.product.stockQuantity} available</p>
                        </td>
                        <td className="px-3 py-4 text-right font-semibold">{money.format(line.product.sellingPrice)}</td>
                        <td className="px-3 py-4">
                          <div className="grid grid-cols-[1fr_72px] gap-1">
                            <select aria-label={`Discount type for ${line.product.name}`} value={line.discountType} onChange={(event) => updateDiscount(line.product.id, event.target.value as DiscountType, line.discountValue)} className="rounded-md border border-slate-300 bg-white px-1 py-2 text-xs outline-none focus:border-indigo-500">
                              <option value="NONE">None</option>
                              <option value="PERCENTAGE">%</option>
                              <option value="FIXED">₹</option>
                            </select>
                            <input aria-label={`Discount value for ${line.product.name}`} disabled={line.discountType === "NONE"} type="number" min="0" step="0.01" value={line.discountValue} onChange={(event) => updateDiscount(line.product.id, line.discountType, Number(event.target.value))} className="min-w-0 rounded-md border border-slate-300 px-2 py-2 text-xs outline-none disabled:bg-slate-100" />
                          </div>
                        </td>
                        <td className="px-5 py-4 text-right text-base font-bold">{calculated ? money.format(calculated.lineTotal) : "…"}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <aside className="flex min-w-0 flex-col gap-4">
          <section className="relative rounded-2xl border-2 border-indigo-500 bg-white p-4 shadow-sm">
            <form onSubmit={scan}>
              <label className="text-xs font-bold uppercase tracking-[0.16em] text-indigo-700" htmlFor="pos-product-search">Barcode / product search</label>
              <div className="mt-2 flex gap-2">
                <input ref={barcodeInput} id="pos-product-search" autoComplete="off" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Scan barcode and press Enter" className="min-w-0 flex-1 rounded-xl border border-slate-300 px-4 py-3 text-base font-semibold outline-none focus:border-indigo-600 focus:ring-4 focus:ring-indigo-100" />
                <button disabled={searching || !query.trim()} className="rounded-xl bg-indigo-700 px-4 font-bold text-white disabled:opacity-50">Add</button>
              </div>
            </form>
            {query.trim().length >= 2 && results.length > 0 && (
              <div className="absolute left-4 right-4 top-[92px] z-30 max-h-80 overflow-auto rounded-xl border border-slate-200 bg-white p-2 shadow-2xl">
                {results.map((product) => (
                  <button key={product.id} type="button" disabled={!product.active || product.stockQuantity <= 0} onClick={() => addProduct(productLookup(product))} className="flex w-full items-center justify-between rounded-lg px-3 py-3 text-left hover:bg-indigo-50 disabled:opacity-50">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold">{product.name}</p>
                      <p className="mt-0.5 truncate text-xs text-slate-500">{product.barcode} · Stock {product.stockQuantity}</p>
                    </div>
                    <span className="ml-3 text-sm font-bold">{money.format(product.sellingPrice)}</span>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between">
              <h3 className="font-bold">Bill discount</h3>
              <select value={taxMode} onChange={(event) => setTaxMode(event.target.value as TaxMode)} className="rounded-lg border border-slate-300 px-2 py-1.5 text-xs font-semibold">
                <option value="INTRA_STATE">CGST + SGST</option>
                <option value="INTER_STATE">IGST</option>
              </select>
            </div>
            <div className="mt-3 grid grid-cols-[1fr_120px] gap-2">
              <SelectInput value={billDiscountType} onChange={(event) => { checkoutKey.current = null; setBillDiscountType(event.target.value as DiscountType); }}>
                <option value="NONE">No discount</option>
                <option value="PERCENTAGE">Percentage</option>
                <option value="FIXED">Fixed amount</option>
              </SelectInput>
              <TextInput disabled={billDiscountType === "NONE"} type="number" min="0" step="0.01" value={billDiscountValue} onChange={(event) => { checkoutKey.current = null; setBillDiscountValue(Number(event.target.value)); }} />
            </div>
          </section>

          <section className="flex flex-1 flex-col rounded-2xl bg-slate-950 p-5 text-white shadow-lg">
            <div className="space-y-3 text-sm">
              <TotalRow label="Subtotal" value={quote?.subtotalAmount} />
              <TotalRow label="Line discounts" value={quote ? -quote.lineDiscountAmount : undefined} muted />
              <TotalRow label="Bill discount" value={quote ? -quote.billDiscountAmount : undefined} muted />
              <div className="border-t border-slate-700 pt-3">
                <TotalRow label="Taxable value" value={quote?.taxableAmount} />
                {taxMode === "INTRA_STATE" ? (
                  <><TotalRow label="CGST" value={quote?.cgstAmount} muted /><TotalRow label="SGST" value={quote?.sgstAmount} muted /></>
                ) : <TotalRow label="IGST" value={quote?.igstAmount} muted />}
                <TotalRow label="Round off" value={quote?.roundOffAmount} muted />
              </div>
            </div>
            <div className="mt-auto border-t border-slate-700 pt-5">
              <div className="flex items-end justify-between">
                <p className="text-sm font-bold uppercase tracking-wider text-slate-400">Payable</p>
                <p className="text-4xl font-black tracking-tight">{quote ? money.format(quote.totalAmount) : money.format(0)}</p>
              </div>
              <p className="mt-2 text-right text-xs text-slate-400">{quoting ? "Recalculating…" : quote?.pricesIncludeGst ? "Prices include GST" : "GST added to prices"}</p>
              <button type="button" disabled={!quote || quoting || quote.totalAmount <= 0} onClick={() => setPaymentOpen(true)} className="mt-5 w-full rounded-xl bg-emerald-500 px-5 py-4 text-lg font-black tracking-wide text-emerald-950 hover:bg-emerald-400 disabled:cursor-not-allowed disabled:opacity-40">CHECKOUT · F2</button>
            </div>
          </section>
        </aside>
      </div>

      {paymentOpen && quote && (
        <PaymentModal
          accessToken={accessToken}
          quote={quote}
          onClose={() => setPaymentOpen(false)}
          onCheckout={async (mode, tendered, reference, customerId) => {
            const key = checkoutKey.current ?? crypto.randomUUID();
            checkoutKey.current = key;
            const completed = await api.checkoutSale(accessToken, key, {
              ...quoteRequest,
              payments: [{
                mode,
                amount: quote.totalAmount,
                ...(mode === "CASH" ? { tenderedAmount: tendered } : {}),
                ...(reference.trim() ? { reference: reference.trim() } : {}),
                ...(mode === "UDHAAR" && customerId ? { customerId } : {})
              }],
              notes: ""
            });
            setPaymentOpen(false);
            setInvoice(completed);
            setCart([]);
            setQuote(null);
            checkoutKey.current = null;
          }}
        />
      )}

      {invoice && (
        <ReceiptModal
          invoice={invoice}
          onClose={() => { setInvoice(null); window.setTimeout(() => barcodeInput.current?.focus(), 0); }}
          onPrint={() => printReceipt(invoice)}
        />
      )}
      {invoice && <ReceiptPrintSurface invoice={invoice} />}
    </div>
  );
}

function Shortcut({ label, text }: { label: string; text: string }) {
  return <span className="rounded-md bg-slate-100 px-2 py-1"><kbd className="text-slate-900">{label}</kbd> {text}</span>;
}

function TotalRow({ label, value, muted = false }: { label: string; value?: number; muted?: boolean }) {
  return <div className={`flex items-center justify-between ${muted ? "text-slate-400" : "text-slate-200"}`}><span>{label}</span><strong>{value === undefined ? "—" : money.format(value)}</strong></div>;
}

function PaymentModal({
  accessToken,
  quote,
  onClose,
  onCheckout
}: {
  accessToken: string;
  quote: PosQuoteResponse;
  onClose: () => void;
  onCheckout: (mode: PaymentMode, tendered: number, reference: string, customerId?: string) => Promise<void>;
}) {
  const [mode, setMode] = useState<PaymentMode>("CASH");
  const [tendered, setTendered] = useState(quote.totalAmount);
  const [reference, setReference] = useState("");
  const [customerQuery, setCustomerQuery] = useState("");
  const [customers, setCustomers] = useState<KhataCustomerResponse[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<KhataCustomerResponse | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const change = mode === "CASH" ? Math.max(0, tendered - quote.totalAmount) : 0;

  useEffect(() => {
    if (mode !== "UDHAAR") return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      api.searchKhataCustomers(accessToken, {
        query: customerQuery,
        active: true,
        balanceStatus: "ALL",
        page: 0,
        size: 8
      }).then((page) => {
        if (!cancelled) setCustomers(page.content);
      }).catch(() => {
        if (!cancelled) setCustomers([]);
      });
    }, customerQuery ? 200 : 0);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [accessToken, customerQuery, mode]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      await onCheckout(mode, tendered, reference, selectedCustomer?.id);
    } catch (caught) {
      setError(messageFrom(caught, "Checkout could not be completed."));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/70 p-6" role="dialog" aria-modal="true" aria-label="Payment">
      <form onSubmit={submit} className="w-full max-w-xl rounded-2xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between">
          <div><p className="text-xs font-bold uppercase tracking-wider text-emerald-700">Complete sale</p><h3 className="mt-1 text-2xl font-bold">Collect {money.format(quote.totalAmount)}</h3></div>
          <button type="button" onClick={onClose} className="rounded-lg px-3 py-2 font-bold text-slate-500 hover:bg-slate-100">✕</button>
        </div>
        {error && <div className="mt-4"><ErrorNotice message={error} /></div>}
        <div className="mt-6 grid grid-cols-4 gap-3">
          {(["CASH", "UPI", "CARD", "UDHAAR"] as PaymentMode[]).map((value) => (
            <button key={value} type="button" onClick={() => setMode(value)} className={`rounded-xl border-2 px-4 py-4 font-bold ${mode === value ? "border-indigo-600 bg-indigo-50 text-indigo-800" : "border-slate-200 hover:border-slate-300"}`}>{value}</button>
          ))}
        </div>
        {mode === "CASH" ? (
          <div className="mt-5 grid grid-cols-2 gap-4">
            <label className="text-sm font-bold text-slate-700">Cash tendered<input autoFocus type="number" min={quote.totalAmount} step="0.01" value={tendered} onChange={(event) => setTendered(Number(event.target.value))} className="mt-2 w-full rounded-xl border border-slate-300 px-4 py-3 text-lg font-bold outline-none focus:border-indigo-600" /></label>
            <div className="rounded-xl bg-emerald-50 p-4"><p className="text-sm font-bold text-emerald-800">Change to return</p><p className="mt-2 text-2xl font-black text-emerald-900">{money.format(change)}</p></div>
          </div>
        ) : mode === "UDHAAR" ? (
          <div className="mt-5">
            <label className="block text-sm font-bold text-slate-700">Credit customer
              <input autoFocus value={customerQuery} onChange={(event) => { setCustomerQuery(event.target.value); setSelectedCustomer(null); }} className="mt-2 w-full rounded-xl border border-slate-300 px-4 py-3 outline-none focus:border-indigo-600" placeholder="Search customer name or phone" />
            </label>
            {selectedCustomer ? (
              <div className="mt-3 flex items-center justify-between rounded-xl border-2 border-emerald-500 bg-emerald-50 p-4">
                <div><p className="font-bold text-emerald-950">{selectedCustomer.name}</p><p className="text-sm text-emerald-800">{selectedCustomer.phone} · Current due {money.format(selectedCustomer.outstandingAmount)}</p></div>
                <button type="button" onClick={() => setSelectedCustomer(null)} className="text-sm font-bold text-emerald-800">Change</button>
              </div>
            ) : (
              <div className="mt-2 max-h-44 overflow-auto rounded-xl border border-slate-200 p-1">
                {customers.length === 0 ? <p className="p-3 text-sm text-slate-500">No active customers found. Create one in Khata first.</p> : customers.map((customer) => (
                  <button key={customer.id} type="button" onClick={() => { setSelectedCustomer(customer); setCustomerQuery(customer.name); }} className="flex w-full items-center justify-between rounded-lg px-3 py-2.5 text-left hover:bg-indigo-50">
                    <div><p className="text-sm font-bold">{customer.name}</p><p className="text-xs text-slate-500">{customer.phone}</p></div>
                    <span className="text-xs font-bold text-red-700">Due {money.format(customer.outstandingAmount)}</span>
                  </button>
                ))}
              </div>
            )}
            <p className="mt-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">This bill will be added to the selected customer’s Khata balance.</p>
          </div>
        ) : (
          <label className="mt-5 block text-sm font-bold text-slate-700">{mode} reference <span className="font-normal text-slate-400">(optional)</span><input autoFocus value={reference} onChange={(event) => setReference(event.target.value)} maxLength={100} className="mt-2 w-full rounded-xl border border-slate-300 px-4 py-3 outline-none focus:border-indigo-600" placeholder="Transaction / approval reference" /></label>
        )}
        <button disabled={saving || (mode === "CASH" && tendered < quote.totalAmount) || (mode === "UDHAAR" && !selectedCustomer)} className="mt-6 w-full rounded-xl bg-emerald-600 px-5 py-4 text-lg font-black text-white hover:bg-emerald-700 disabled:opacity-50">{saving ? "Saving sale…" : "SAVE BILL & OPEN RECEIPT"}</button>
      </form>
    </div>
  );
}

function ReceiptModal({ invoice, onClose, onPrint }: { invoice: PosInvoiceResponse; onClose: () => void; onPrint: () => Promise<void> }) {
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/70 p-6" role="dialog" aria-modal="true" aria-label="Completed receipt">
      <div className="flex max-h-[92vh] w-full max-w-2xl flex-col rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 p-5"><div><p className="text-sm font-bold text-emerald-700">Sale completed</p><h3 className="text-xl font-bold">Invoice {invoice.invoiceNumber}</h3></div><button onClick={onClose} className="rounded-lg px-3 py-2 font-bold hover:bg-slate-100">✕</button></div>
        <div className="min-h-0 overflow-auto bg-slate-100 p-6"><div className="mx-auto shadow-xl"><ThermalReceipt invoice={invoice} /></div></div>
        <div className="flex justify-end gap-3 border-t border-slate-200 p-5"><button onClick={onClose} className="rounded-lg border border-slate-300 px-5 py-2.5 font-bold">New sale</button><button onClick={() => void onPrint()} className="rounded-lg bg-indigo-700 px-5 py-2.5 font-bold text-white">Print receipt · F4</button></div>
      </div>
    </div>
  );
}

function ReceiptPrintSurface({ invoice }: { invoice: PosInvoiceResponse }) {
  const width = invoice.store.receiptWidth === "MM_58" ? 58 : 80;
  return <><style>{`@media print { @page { size: ${width}mm 297mm; margin: 0; } }`}</style><div className="receipt-print-surface" aria-hidden="true"><ThermalReceipt invoice={invoice} /></div></>;
}

function ThermalReceipt({ invoice }: { invoice: PosInvoiceResponse }) {
  const width = invoice.store.receiptWidth === "MM_58" ? 58 : 80;
  const compact = width === 58;
  return (
    <article className="thermal-receipt bg-white font-mono text-black" style={{ width: `${width}mm`, padding: compact ? "3mm" : "4mm", fontSize: compact ? "8pt" : "9pt", lineHeight: 1.35 }}>
      <header className="text-center"><h1 className="font-sans text-base font-black">{invoice.store.shopName}</h1><p>{invoice.store.address}</p><p>Phone: {invoice.store.phone}</p>{invoice.store.gstin && <p>GSTIN: {invoice.store.gstin}</p>}</header>
      <div className="my-2 border-y border-dashed border-black py-1"><div className="flex justify-between"><span>Bill: {invoice.invoiceNumber}</span><span>{new Date(invoice.completedAt).toLocaleDateString("en-IN")}</span></div><div className="flex justify-between"><span>{new Date(invoice.completedAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}</span><span>{invoice.totals.taxMode === "INTRA_STATE" ? "Local" : "Interstate"}</span></div></div>
      <table className="w-full table-fixed"><thead><tr className="border-b border-black"><th className="w-[48%] text-left">Item</th><th className="w-[14%] text-right">Qty</th><th className="w-[18%] text-right">Rate</th><th className="w-[20%] text-right">Amt</th></tr></thead><tbody>{invoice.totals.lines.map((line) => <tr key={line.lineNumber} className="align-top"><td className="pr-1">{line.receiptName}</td><td className="text-right">{line.quantity}</td><td className="text-right">{line.unitPrice.toFixed(2)}</td><td className="text-right">{line.lineTotal.toFixed(2)}</td></tr>)}</tbody></table>
      <div className="mt-2 border-t border-dashed border-black pt-1"><ReceiptRow label="Subtotal" value={invoice.totals.subtotalAmount} />{invoice.totals.lineDiscountAmount + invoice.totals.billDiscountAmount > 0 && <ReceiptRow label="Discount" value={-(invoice.totals.lineDiscountAmount + invoice.totals.billDiscountAmount)} />}<ReceiptRow label="Taxable" value={invoice.totals.taxableAmount} />{invoice.totals.taxMode === "INTRA_STATE" ? <><ReceiptRow label="CGST" value={invoice.totals.cgstAmount} /><ReceiptRow label="SGST" value={invoice.totals.sgstAmount} /></> : <ReceiptRow label="IGST" value={invoice.totals.igstAmount} />}<ReceiptRow label="Round off" value={invoice.totals.roundOffAmount} /><div className="mt-1 flex justify-between border-y border-black py-1 text-lg font-black"><span>TOTAL</span><span>₹{invoice.totals.totalAmount.toFixed(2)}</span></div>{invoice.payments.map((payment, index) => <div key={index}><ReceiptRow label={payment.customerName ? `${payment.mode} · ${payment.customerName}` : payment.mode} value={payment.amount} />{payment.changeAmount > 0 && <ReceiptRow label="Change" value={payment.changeAmount} />}</div>)}</div>
      <footer className="mt-3 text-center"><p className="font-bold">Thank you. Visit again!</p><p className="mt-1 text-[0.85em]">Computer-generated invoice</p></footer>
    </article>
  );
}

function ReceiptRow({ label, value }: { label: string; value: number }) {
  return <div className="flex justify-between"><span>{label}</span><span>{value < 0 ? "-" : ""}₹{Math.abs(value).toFixed(2)}</span></div>;
}
