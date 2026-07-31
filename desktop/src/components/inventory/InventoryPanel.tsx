import { useEffect, useState } from "react";
import { api } from "../../lib/api";
import type {
  CategoryResponse,
  InventoryPage,
  ProductResponse,
  ProductSort,
  StockStatus,
  UnitResponse
} from "../../types";
import { ErrorNotice, SelectInput, SuccessNotice, TextInput } from "../FormControls";
import { CategoryManagerModal } from "./CategoryManagerModal";
import { ProductEditorModal } from "./ProductEditorModal";
import { StockLedgerModal } from "./StockLedgerModal";

const emptyPage: InventoryPage<ProductResponse> = {
  content: [],
  page: 0,
  size: 25,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true
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

function formatQuantity(value: number) {
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 3 }).format(value);
}

export function InventoryPanel({
  accessToken,
  canWrite
}: {
  accessToken: string;
  canWrite: boolean;
}) {
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [units, setUnits] = useState<UnitResponse[]>([]);
  const [products, setProducts] = useState<InventoryPage<ProductResponse>>(emptyPage);
  const [query, setQuery] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [stockStatus, setStockStatus] = useState<StockStatus>("ALL");
  const [activeFilter, setActiveFilter] = useState<"active" | "inactive" | "all">("active");
  const [sort, setSort] = useState<ProductSort>("NAME_ASC");
  const [page, setPage] = useState(0);
  const [lowStockCount, setLowStockCount] = useState(0);
  const [outOfStockCount, setOutOfStockCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [referenceVersion, setReferenceVersion] = useState(0);
  const [showCategories, setShowCategories] = useState(false);
  const [editingProduct, setEditingProduct] = useState<ProductResponse | null | undefined>(undefined);
  const [stockProduct, setStockProduct] = useState<ProductResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.listCategories(accessToken),
      api.listUnits(accessToken)
    ])
      .then(([loadedCategories, loadedUnits]) => {
        if (!cancelled) {
          setCategories(loadedCategories);
          setUnits(loadedUnits);
        }
      })
      .catch((caught) => {
        if (!cancelled) setError(messageFrom(caught, "Inventory reference data could not be loaded."));
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, referenceVersion]);

  useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setLoading(true);
      setError("");
      api.searchProducts(accessToken, {
        query,
        categoryId,
        active: activeFilter === "all" ? null : activeFilter === "active",
        stockStatus,
        page,
        size: 25,
        sort
      })
        .then((result) => {
          if (!cancelled) setProducts(result);
        })
        .catch((caught) => {
          if (!cancelled) setError(messageFrom(caught, "Products could not be loaded."));
        })
        .finally(() => {
          if (!cancelled) setLoading(false);
        });
    }, query ? 250 : 0);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [accessToken, query, categoryId, activeFilter, stockStatus, page, sort, refreshVersion]);

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      api.getStockAlerts(accessToken, "LOW_STOCK", 0, 1),
      api.getStockAlerts(accessToken, "OUT_OF_STOCK", 0, 1)
    ])
      .then(([low, out]) => {
        if (!cancelled) {
          setLowStockCount(low.totalElements);
          setOutOfStockCount(out.totalElements);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLowStockCount(0);
          setOutOfStockCount(0);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, refreshVersion]);

  function refresh(message?: string) {
    if (message) setSuccess(message);
    setError("");
    setRefreshVersion((value) => value + 1);
  }

  function resetToFirstPage() {
    setPage(0);
  }

  return (
    <div className="mx-auto max-w-[1500px] space-y-6">
      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}

      <section className="grid grid-cols-[1.4fr_1fr_1fr] gap-5">
        <article className="rounded-2xl bg-indigo-700 p-6 text-white shadow-sm">
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-indigo-200">Catalog</p>
          <div className="mt-3 flex items-end justify-between">
            <div>
              <p className="text-4xl font-bold">{products.totalElements}</p>
              <p className="mt-1 text-sm text-indigo-100">Products matching current filters</p>
            </div>
            {canWrite && (
              <button
                type="button"
                onClick={() => setEditingProduct(null)}
                className="rounded-xl bg-white px-5 py-3 text-sm font-bold text-indigo-800 hover:bg-indigo-50"
              >
                Add product
              </button>
            )}
          </div>
        </article>
        <button
          type="button"
          onClick={() => {
            setStockStatus("LOW_STOCK");
            setActiveFilter("active");
            setPage(0);
          }}
          className="rounded-2xl border border-amber-200 bg-amber-50 p-6 text-left shadow-sm transition hover:border-amber-300"
        >
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-amber-700">Low stock</p>
          <p className="mt-3 text-4xl font-bold text-amber-950">{lowStockCount}</p>
          <p className="mt-1 text-sm text-amber-800">At or below minimum level</p>
        </button>
        <button
          type="button"
          onClick={() => {
            setStockStatus("OUT_OF_STOCK");
            setActiveFilter("active");
            setPage(0);
          }}
          className="rounded-2xl border border-red-200 bg-red-50 p-6 text-left shadow-sm transition hover:border-red-300"
        >
          <p className="text-xs font-bold uppercase tracking-[0.18em] text-red-700">Out of stock</p>
          <p className="mt-3 text-4xl font-bold text-red-950">{outOfStockCount}</p>
          <p className="mt-1 text-sm text-red-800">Unavailable for normal billing</p>
        </button>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <header className="border-b border-slate-200 p-5">
          <div className="flex items-center justify-between gap-5">
            <div>
              <h3 className="text-lg font-bold">Products</h3>
              <p className="mt-1 text-sm text-slate-500">Search by name, receipt name, SKU or barcode.</p>
            </div>
            {canWrite && (
              <button
                type="button"
                onClick={() => setShowCategories(true)}
                className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50"
              >
                Manage categories
              </button>
            )}
          </div>
          <div className="mt-5 grid grid-cols-[minmax(260px,1.4fr)_1fr_170px_150px_170px] gap-3">
            <TextInput
              className="mt-0"
              placeholder="Search products or scan a barcode"
              value={query}
              onChange={(event) => {
                setQuery(event.target.value);
                resetToFirstPage();
              }}
            />
            <SelectInput
              className="mt-0"
              value={categoryId}
              onChange={(event) => {
                setCategoryId(event.target.value);
                resetToFirstPage();
              }}
            >
              <option value="">All categories</option>
              {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
            </SelectInput>
            <SelectInput
              className="mt-0"
              value={stockStatus}
              onChange={(event) => {
                setStockStatus(event.target.value as StockStatus);
                resetToFirstPage();
              }}
            >
              <option value="ALL">All stock</option>
              <option value="IN_STOCK">In stock</option>
              <option value="LOW_STOCK">Low stock</option>
              <option value="OUT_OF_STOCK">Out of stock</option>
            </SelectInput>
            <SelectInput
              className="mt-0"
              value={activeFilter}
              onChange={(event) => {
                setActiveFilter(event.target.value as typeof activeFilter);
                resetToFirstPage();
              }}
            >
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
              <option value="all">All status</option>
            </SelectInput>
            <SelectInput
              className="mt-0"
              value={sort}
              onChange={(event) => {
                setSort(event.target.value as ProductSort);
                resetToFirstPage();
              }}
            >
              <option value="NAME_ASC">Name A-Z</option>
              <option value="NAME_DESC">Name Z-A</option>
              <option value="UPDATED_DESC">Recently updated</option>
              <option value="PRICE_ASC">Selling price</option>
              <option value="STOCK_ASC">Lowest stock</option>
            </SelectInput>
          </div>
        </header>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[1120px] border-collapse text-left">
            <thead className="bg-slate-50 text-xs font-bold uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-5 py-3">Product</th>
                <th className="px-4 py-3">Barcode / SKU</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3 text-right">Cost</th>
                <th className="px-4 py-3 text-right">Selling</th>
                <th className="px-4 py-3">Stock</th>
                <th className="px-5 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr><td colSpan={7} className="px-5 py-12 text-center text-sm text-slate-500">Loading products...</td></tr>
              ) : products.content.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-14 text-center">
                    <p className="font-bold text-slate-700">No products found</p>
                    <p className="mt-1 text-sm text-slate-500">Change the filters or add the first product.</p>
                  </td>
                </tr>
              ) : products.content.map((product) => (
                <ProductRow
                  key={`${product.id}-${product.version}-${product.stockVersion}`}
                  product={product}
                  canWrite={canWrite}
                  onEdit={() => setEditingProduct(product)}
                  onStock={() => setStockProduct(product)}
                />
              ))}
            </tbody>
          </table>
        </div>

        <footer className="flex items-center justify-between border-t border-slate-200 px-5 py-4">
          <p className="text-sm text-slate-500">
            {products.totalElements === 0
              ? "0 products"
              : `${products.page * products.size + 1}-${Math.min((products.page + 1) * products.size, products.totalElements)} of ${products.totalElements}`}
          </p>
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={products.first || loading}
              onClick={() => setPage((value) => Math.max(0, value - 1))}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50 disabled:opacity-40"
            >
              Previous
            </button>
            <span className="px-2 text-sm font-semibold text-slate-600">
              Page {products.totalPages === 0 ? 0 : products.page + 1} of {products.totalPages}
            </span>
            <button
              type="button"
              disabled={products.last || loading}
              onClick={() => setPage((value) => value + 1)}
              className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-bold hover:bg-slate-50 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        </footer>
      </section>

      {showCategories && (
        <CategoryManagerModal
          accessToken={accessToken}
          onClose={() => setShowCategories(false)}
          onChanged={() => {
            setReferenceVersion((value) => value + 1);
            refresh();
          }}
        />
      )}
      {editingProduct !== undefined && (
        <ProductEditorModal
          accessToken={accessToken}
          product={editingProduct}
          categories={categories}
          units={units}
          onClose={() => setEditingProduct(undefined)}
          onSaved={(_saved, message) => {
            setEditingProduct(undefined);
            refresh(message);
          }}
        />
      )}
      {stockProduct && (
        <StockLedgerModal
          accessToken={accessToken}
          product={stockProduct}
          canWrite={canWrite}
          onClose={() => setStockProduct(null)}
          onAdjusted={(updated) => {
            setStockProduct(updated);
            refresh("Stock adjusted and ledger entry recorded.");
          }}
        />
      )}
    </div>
  );
}

function ProductRow({
  product,
  canWrite,
  onEdit,
  onStock
}: {
  product: ProductResponse;
  canWrite: boolean;
  onEdit: () => void;
  onStock: () => void;
}) {
  return (
    <tr className={`${product.active ? "bg-white" : "bg-slate-50 opacity-70"} hover:bg-indigo-50/30`}>
      <td className="px-5 py-4">
        <div className="flex items-start gap-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-indigo-100 text-sm font-black text-indigo-700">
            {product.name.slice(0, 2).toUpperCase()}
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-bold text-slate-950">{product.name}</p>
            <p className="mt-0.5 truncate text-xs text-slate-500">{product.receiptName}</p>
            {!product.active && <span className="mt-1 inline-block rounded bg-slate-200 px-1.5 py-0.5 text-[10px] font-bold text-slate-600">INACTIVE</span>}
          </div>
        </div>
      </td>
      <td className="px-4 py-4">
        <p className="font-mono text-xs font-semibold text-slate-800">{product.barcode}</p>
        <p className="mt-1 text-xs text-slate-500">{product.sku ?? "No SKU"}{product.internalBarcode ? " - Internal" : ""}</p>
      </td>
      <td className="px-4 py-4">
        <p className="text-sm font-semibold">{product.category.name}</p>
        <p className="mt-1 text-xs text-slate-500">{product.unit.replaceAll("_", " ")}</p>
      </td>
      <td className="px-4 py-4 text-right text-sm text-slate-600">{formatMoney(product.purchaseCost)}</td>
      <td className="px-4 py-4 text-right text-sm font-bold">{formatMoney(product.sellingPrice)}</td>
      <td className="px-4 py-4">
        <p className="text-sm font-bold">{formatQuantity(product.stockQuantity)}</p>
        <StockBadge status={product.stockStatus} />
      </td>
      <td className="px-5 py-4">
        <div className="flex justify-end gap-2">
          <button type="button" onClick={onStock} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-bold hover:bg-slate-50">
            {canWrite ? "Adjust / ledger" : "View ledger"}
          </button>
          {canWrite && (
            <button type="button" onClick={onEdit} className="rounded-lg bg-indigo-700 px-3 py-2 text-xs font-bold text-white hover:bg-indigo-800">
              Edit
            </button>
          )}
        </div>
      </td>
    </tr>
  );
}

function StockBadge({ status }: { status: ProductResponse["stockStatus"] }) {
  const classes = {
    IN_STOCK: "bg-emerald-50 text-emerald-700",
    LOW_STOCK: "bg-amber-50 text-amber-800",
    OUT_OF_STOCK: "bg-red-50 text-red-700"
  };
  return (
    <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-[10px] font-bold ${classes[status]}`}>
      {status.replaceAll("_", " ")}
    </span>
  );
}
