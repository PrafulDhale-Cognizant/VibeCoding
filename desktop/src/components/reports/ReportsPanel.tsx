import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  DashboardReportResponse,
  InvoiceActivityResponse,
  InvoiceOutputType,
  InvoiceSummaryResponse,
  PaymentMode,
  PosInvoiceResponse,
  ReportStockAlertResponse,
  SalesReportResponse,
  SalesSummaryResponse
} from "../../types";
import { useStoreLogo } from "../../hooks/useStoreLogo";
import { ErrorNotice, Field, SelectInput, SuccessNotice, TextInput } from "../FormControls";
import { ReceiptPrintSurface, ThermalReceipt } from "../pos/PosPanel";

const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2
});

const paymentLabels: Record<PaymentMode, string> = {
  CASH: "Cash",
  UPI: "UPI",
  CARD: "Card",
  UDHAAR: "Udhaar"
};

function localIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function initialRange() {
  const today = new Date();
  return {
    from: localIsoDate(new Date(today.getFullYear(), today.getMonth(), 1)),
    to: localIsoDate(today)
  };
}

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

export function ReportsPanel({ accessToken, canViewInvoices, onStartReturn }: {
  accessToken: string;
  canViewInvoices: boolean;
  onStartReturn: (invoiceNumber: string) => void;
}) {
  const initial = useMemo(initialRange, []);
  const logoUrl = useStoreLogo(accessToken);
  const [dashboard, setDashboard] = useState<DashboardReportResponse | null>(null);
  const [sales, setSales] = useState<SalesReportResponse | null>(null);
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [loading, setLoading] = useState(true);
  const [reportLoading, setReportLoading] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState("");
  const [invoiceQuery, setInvoiceQuery] = useState("");
  const [invoicePage, setInvoicePage] = useState(0);
  const [invoices, setInvoices] = useState<InvoiceSummaryResponse[]>([]);
  const [invoiceTotal, setInvoiceTotal] = useState(0);
  const [invoicePages, setInvoicePages] = useState(0);
  const [selectedInvoice, setSelectedInvoice] = useState<PosInvoiceResponse | null>(null);
  const [invoiceActivities, setInvoiceActivities] = useState<InvoiceActivityResponse[]>([]);
  const [invoiceLoadingId, setInvoiceLoadingId] = useState<string | null>(null);
  const [invoicePrinting, setInvoicePrinting] = useState(false);
  const [invoiceOutputMode, setInvoiceOutputMode] = useState<"THERMAL" | "A4">("THERMAL");
  const [invoiceStatus, setInvoiceStatus] = useState<PosInvoiceResponse["status"] | "ALL">("ALL");
  const [invoicePaymentMode, setInvoicePaymentMode] = useState<PaymentMode | "ALL">("ALL");
  const [invoiceFrom, setInvoiceFrom] = useState("");
  const [invoiceTo, setInvoiceTo] = useState("");
  const [invoiceMinAmount, setInvoiceMinAmount] = useState("");
  const [invoiceMaxAmount, setInvoiceMaxAmount] = useState("");
  const [invoiceSort, setInvoiceSort] = useState<"NEWEST" | "OLDEST" | "AMOUNT_HIGH" | "AMOUNT_LOW">("NEWEST");
  const [selectedInvoiceIds, setSelectedInvoiceIds] = useState<Set<string>>(new Set());
  const [success, setSuccess] = useState("");

  const loadDashboard = useCallback(async () => {
    const response = await api.getDashboardReport(accessToken);
    setDashboard(response);
  }, [accessToken]);

  const loadSales = useCallback(async (rangeFrom: string, rangeTo: string) => {
    const response = await api.getSalesReport(accessToken, rangeFrom, rangeTo);
    setSales(response);
  }, [accessToken]);

  useEffect(() => {
    setLoading(true);
    setError("");
    Promise.all([loadDashboard(), loadSales(initial.from, initial.to)])
      .catch((caught) => setError(messageFrom(caught, "Reports could not be loaded.")))
      .finally(() => setLoading(false));
  }, [initial.from, initial.to, loadDashboard, loadSales]);

  useEffect(() => {
    if (!canViewInvoices) return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      let from: string | undefined;
      let to: string | undefined;
      if (invoiceFrom) from = new Date(`${invoiceFrom}T00:00:00`).toISOString();
      if (invoiceTo) {
        const end = new Date(`${invoiceTo}T00:00:00`);
        end.setDate(end.getDate() + 1);
        to = end.toISOString();
      }
      api.searchInvoices(accessToken, {
        query: invoiceQuery.trim(),
        status: invoiceStatus,
        paymentMode: invoicePaymentMode,
        from,
        to,
        minAmount: invoiceMinAmount === "" ? null : Number(invoiceMinAmount),
        maxAmount: invoiceMaxAmount === "" ? null : Number(invoiceMaxAmount),
        sort: invoiceSort,
        page: invoicePage,
        size: 20
      })
        .then((page) => { if (!cancelled) { setInvoices(page.content); setInvoiceTotal(page.totalElements); setInvoicePages(page.totalPages); } })
        .catch((caught) => { if (!cancelled) setError(messageFrom(caught, "Invoices could not be loaded.")); });
    }, invoiceQuery.trim() ? 220 : 0);
    return () => { cancelled = true; window.clearTimeout(timer); };
  }, [accessToken, canViewInvoices, invoiceFrom, invoiceMaxAmount, invoiceMinAmount, invoicePage, invoicePaymentMode, invoiceQuery, invoiceSort, invoiceStatus, invoiceTo]);

  useEffect(() => {
    setSelectedInvoiceIds(new Set());
  }, [invoiceFrom, invoiceMaxAmount, invoiceMinAmount, invoicePage, invoicePaymentMode, invoiceQuery, invoiceSort, invoiceStatus, invoiceTo]);

  async function applyRange(event: FormEvent) {
    event.preventDefault();
    setReportLoading(true);
    setError("");
    try {
      await loadSales(from, to);
    } catch (caught) {
      setError(messageFrom(caught, "The sales report could not be loaded."));
    } finally {
      setReportLoading(false);
    }
  }

  async function refreshDashboard() {
    setLoading(true);
    setError("");
    try {
      await Promise.all([loadDashboard(), loadSales(from, to)]);
    } catch (caught) {
      setError(messageFrom(caught, "Reports could not be refreshed."));
    } finally {
      setLoading(false);
    }
  }

  async function printReport() {
    if (!sales) return;
    setPrinting(true);
    setError("");
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      if (window.billingDesktop?.printReport) {
        await window.billingDesktop.printReport();
      } else {
        window.print();
      }
    } catch (caught) {
      setError(messageFrom(caught, "The report could not be printed."));
    } finally {
      setPrinting(false);
    }
  }

  async function openInvoice(invoiceId: string) {
    setInvoiceLoadingId(invoiceId);
    setError("");
    setSuccess("");
    try {
      const [invoice, activity] = await Promise.all([
        api.getInvoice(accessToken, invoiceId),
        api.getInvoiceActivity(accessToken, invoiceId)
      ]);
      setSelectedInvoice(invoice);
      setInvoiceActivities(activity);
      setInvoiceOutputMode("THERMAL");
    } catch (caught) {
      setError(messageFrom(caught, "The invoice could not be opened."));
    } finally {
      setInvoiceLoadingId(null);
    }
  }

  async function refreshInvoiceActivity(invoiceId: string) {
    setInvoiceActivities(await api.getInvoiceActivity(accessToken, invoiceId));
  }

  async function recordInvoiceOutput(invoiceId: string, type: InvoiceOutputType) {
    await api.recordInvoiceOutput(accessToken, invoiceId, type);
    await refreshInvoiceActivity(invoiceId);
  }

  async function reprintInvoice() {
    if (!selectedInvoice) return;
    setInvoicePrinting(true);
    setError("");
    setSuccess("");
    setInvoiceOutputMode("THERMAL");
    try {
      await new Promise<void>((resolve) => {
        window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve()));
      });
      const widthMm = selectedInvoice.store.receiptWidth === "MM_58" ? 58 : 80;
      if (window.billingDesktop?.printReceipt) {
        const printed = await window.billingDesktop.printReceipt({ widthMm });
        if (!printed) return;
      } else {
        window.print();
      }
      await recordInvoiceOutput(selectedInvoice.id, "THERMAL_REPRINT");
      setSuccess(`Invoice ${selectedInvoice.invoiceNumber} sent to the receipt printer.`);
    } catch (caught) {
      setError(messageFrom(caught, "The invoice could not be reprinted."));
    } finally {
      setInvoicePrinting(false);
    }
  }

  async function printInvoiceA4() {
    if (!selectedInvoice) return;
    setInvoicePrinting(true);
    setError("");
    setSuccess("");
    setInvoiceOutputMode("A4");
    try {
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve())));
      if (window.billingDesktop?.printReport) {
        const printed = await window.billingDesktop.printReport();
        if (!printed) return;
      } else window.print();
      await recordInvoiceOutput(selectedInvoice.id, "A4_PRINT");
      setSuccess(`A4 invoice ${selectedInvoice.invoiceNumber} sent to the printer.`);
    } catch (caught) {
      setError(messageFrom(caught, "The A4 invoice could not be printed."));
    } finally {
      setInvoicePrinting(false);
    }
  }

  async function saveInvoicePdf() {
    if (!selectedInvoice) return;
    setInvoicePrinting(true);
    setError("");
    setSuccess("");
    setInvoiceOutputMode("A4");
    try {
      await new Promise<void>((resolve) => window.requestAnimationFrame(() => window.requestAnimationFrame(() => resolve())));
      if (!window.billingDesktop?.saveInvoicePdf) {
        window.print();
        return;
      }
      const saved = await window.billingDesktop.saveInvoicePdf(selectedInvoice.invoiceNumber);
      if (!saved) return;
      await recordInvoiceOutput(selectedInvoice.id, "PDF_EXPORT");
      setSuccess(`${saved.fileName} saved successfully.`);
    } catch (caught) {
      setError(messageFrom(caught, "The invoice PDF could not be saved."));
    } finally {
      setInvoicePrinting(false);
    }
  }

  async function copyInvoiceShareText() {
    if (!selectedInvoice) return;
    const customer = selectedInvoice.payments.find((payment) => payment.customerName);
    const text = [
      selectedInvoice.store.shopName,
      `Invoice ${selectedInvoice.invoiceNumber}`,
      `Date: ${new Date(selectedInvoice.completedAt).toLocaleString("en-IN")}`,
      customer?.customerName ? `Customer: ${customer.customerName}` : null,
      `Total: ${money.format(selectedInvoice.totals.totalAmount)}`,
      `Status: ${selectedInvoice.status.replaceAll("_", " ")}`
    ].filter(Boolean).join("\n");
    try {
      await navigator.clipboard.writeText(text);
      await recordInvoiceOutput(selectedInvoice.id, "SHARE_COPIED");
      setSuccess("Invoice summary copied. Paste it into WhatsApp, email, or another app.");
    } catch (caught) {
      setError(messageFrom(caught, "The invoice summary could not be copied."));
    }
  }

  function exportInvoicesCsv() {
    const selected = selectedInvoiceIds.size
      ? invoices.filter((invoice) => selectedInvoiceIds.has(invoice.id))
      : invoices;
    if (!selected.length) return;
    const rows = [
      ["Invoice", "Date", "Customer", "Phone", "Status", "Total", "Returnable"],
      ...selected.map((invoice) => [
        invoice.invoiceNumber,
        invoice.completedAt,
        invoice.customerName ?? "Walk-in",
        invoice.customerPhone ?? "",
        invoice.status,
        invoice.totalAmount.toFixed(2),
        invoice.returnableTotal.toFixed(2)
      ])
    ];
    const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob(["\ufeff", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `invoices-${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
    setSuccess(`${selected.length} invoice${selected.length === 1 ? "" : "s"} exported.`);
  }

  function resetInvoiceFilters() {
    setInvoiceQuery("");
    setInvoiceStatus("ALL");
    setInvoicePaymentMode("ALL");
    setInvoiceFrom("");
    setInvoiceTo("");
    setInvoiceMinAmount("");
    setInvoiceMaxAmount("");
    setInvoiceSort("NEWEST");
    setInvoicePage(0);
    setSelectedInvoiceIds(new Set());
  }

  function exportCsv() {
    if (!sales) return;
    const summary = sales.summary;
    const rows: Array<Array<string | number>> = [
      ["Simplified Billing - Sales Report"],
      ["Shop", sales.shopName],
      ["From", sales.from, "To", sales.to, "Timezone", sales.timezone],
      [],
      ["Business date", "Bills", "Sales", "Snapshot cost", "Gross margin"],
      ...sales.dailySales.map((day) => [
        day.businessDate,
        day.billCount,
        day.totalSales.toFixed(2),
        day.snapshotCost.toFixed(2),
        day.grossMargin.toFixed(2)
      ]),
      [],
      ["Period totals", summary.billCount, summary.totalSales.toFixed(2),
        summary.snapshotCost.toFixed(2), summary.grossMargin.toFixed(2)],
      ["Discount", summary.discountAmount.toFixed(2)],
      ["CGST", summary.cgstAmount.toFixed(2)],
      ["SGST", summary.sgstAmount.toFixed(2)],
      ["IGST", summary.igstAmount.toFixed(2)],
      [],
      ["Payment mode", "Amount"],
      ...Object.entries(summary.paymentTotals).map(([mode, amount]) => [mode, amount.toFixed(2)])
    ];
    const csv = rows
      .map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(","))
      .join("\r\n");
    const url = URL.createObjectURL(new Blob(["\ufeff", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `sales-report-${sales.from}-to-${sales.to}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  if (loading && !dashboard) {
    return <p className="text-sm text-slate-600">Loading dashboard and reports…</p>;
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}

      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-slate-500">
            {dashboard ? `${dashboard.businessDate} · ${dashboard.timezone}` : "Current business day"}
          </p>
          <h3 className="mt-1 text-2xl font-bold">Today at a glance</h3>
        </div>
        <div className="flex gap-3">
          {canViewInvoices && <a href="#invoice-archive" className="rounded-lg bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-indigo-800">Find / reprint invoice</a>}
          <button
            type="button"
            onClick={() => void refreshDashboard()}
            disabled={loading}
            className="rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
          >
            {loading ? "Refreshing…" : "Refresh dashboard"}
          </button>
        </div>
      </div>

      {dashboard && (
        <>
          <section className="grid grid-cols-4 gap-4">
            <MetricCard label="Today's sales" value={money.format(dashboard.today.totalSales)} tone="indigo" />
            <MetricCard label="Bills completed" value={String(dashboard.today.billCount)} tone="slate" />
            <MetricCard label="Gross margin" value={money.format(dashboard.today.grossMargin)} tone="green" />
            <MetricCard label="Khata outstanding" value={money.format(dashboard.credit.totalOutstanding)} tone="red" />
          </section>

          <section className="grid grid-cols-4 gap-4">
            <MetricCard label="Month-to-date sales" value={money.format(dashboard.monthToDate.totalSales)} tone="indigo" />
            <MetricCard label="Month profit" value={money.format(dashboard.monthToDate.grossMargin)} tone="green" />
            <MetricCard label="Year-to-date sales" value={money.format(dashboard.yearToDate.totalSales)} tone="slate" />
            <MetricCard label="Active customers" value={String(dashboard.credit.activeCustomers)} tone="slate" />
          </section>

          <section className="grid grid-cols-[1.35fr_1fr_1fr] gap-5">
            <RevenueTrend days={dashboard.revenueTrend} />
            <TopProducts products={dashboard.topSellingProducts} />
            <RecentTransactions transactions={dashboard.recentTransactions} />
          </section>

          <section className="grid grid-cols-[1.05fr_1fr_1fr] gap-5">
            <PaymentBreakdown summary={dashboard.today} />
            <AlertCard
              title="Low stock"
              count={dashboard.inventory.lowStockCount}
              items={dashboard.inventory.lowStockItems}
              tone="amber"
            />
            <AlertCard
              title="Out of stock"
              count={dashboard.inventory.outOfStockCount}
              items={dashboard.inventory.outOfStockItems}
              tone="red"
            />
          </section>
        </>
      )}

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-end justify-between gap-5 border-b border-slate-200 p-6">
          <form onSubmit={applyRange} className="flex items-end gap-4">
            <Field label="From">
              <TextInput type="date" required value={from} onChange={(event) => setFrom(event.target.value)} />
            </Field>
            <Field label="To">
              <TextInput type="date" required value={to} onChange={(event) => setTo(event.target.value)} />
            </Field>
            <button
              disabled={reportLoading}
              className="mb-px rounded-lg bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-50"
            >
              {reportLoading ? "Loading…" : "Run report"}
            </button>
          </form>
          <div className="flex gap-3">
            <button
              type="button"
              disabled={!sales}
              onClick={exportCsv}
              className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-40"
            >
              Export CSV
            </button>
            <button
              type="button"
              disabled={!sales || printing}
              onClick={() => void printReport()}
              className="rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-bold text-white hover:bg-slate-800 disabled:opacity-40"
            >
              {printing ? "Opening print…" : "Print report"}
            </button>
          </div>
        </div>

        {sales ? <SalesReportBody report={sales} /> : <p className="p-6 text-sm text-slate-500">Run a date-range report to view sales.</p>}
      </section>

      {canViewInvoices && <section id="invoice-archive" className="scroll-mt-6 rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 p-5">
          <div className="flex items-center justify-between gap-4">
            <div><h4 className="font-bold">Invoice archive</h4><p className="text-xs text-slate-500">{invoiceTotal} matching invoices · view, return, print, export, or share</p></div>
            <div className="flex gap-2"><button type="button" onClick={resetInvoiceFilters} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-bold">Clear filters</button><button type="button" disabled={invoices.length === 0} onClick={exportInvoicesCsv} className="rounded-lg bg-slate-900 px-3 py-2 text-xs font-bold text-white disabled:opacity-40">Export {selectedInvoiceIds.size ? "selected" : "page"} CSV</button></div>
          </div>
          <div className="mt-4 grid grid-cols-7 gap-3">
            <Field label="Search"><TextInput className="mt-0" value={invoiceQuery} onChange={(event) => { setInvoiceQuery(event.target.value); setInvoicePage(0); }} placeholder="Number, customer, phone" /></Field>
            <Field label="Status"><SelectInput value={invoiceStatus} onChange={(event) => { setInvoiceStatus(event.target.value as typeof invoiceStatus); setInvoicePage(0); }}><option value="ALL">All statuses</option><option value="COMPLETED">Completed</option><option value="PARTIALLY_RETURNED">Partially returned</option><option value="RETURNED">Returned</option><option value="CANCELLED">Cancelled</option></SelectInput></Field>
            <Field label="Payment"><SelectInput value={invoicePaymentMode} onChange={(event) => { setInvoicePaymentMode(event.target.value as typeof invoicePaymentMode); setInvoicePage(0); }}><option value="ALL">All modes</option><option value="CASH">Cash</option><option value="UPI">UPI</option><option value="CARD">Card</option><option value="UDHAAR">Udhaar</option></SelectInput></Field>
            <Field label="From"><TextInput type="date" value={invoiceFrom} onChange={(event) => { setInvoiceFrom(event.target.value); setInvoicePage(0); }} /></Field>
            <Field label="To"><TextInput type="date" value={invoiceTo} onChange={(event) => { setInvoiceTo(event.target.value); setInvoicePage(0); }} /></Field>
            <Field label="Min amount"><TextInput type="number" min="0" step="0.01" value={invoiceMinAmount} onChange={(event) => { setInvoiceMinAmount(event.target.value); setInvoicePage(0); }} /></Field>
            <Field label="Max amount"><TextInput type="number" min="0" step="0.01" value={invoiceMaxAmount} onChange={(event) => { setInvoiceMaxAmount(event.target.value); setInvoicePage(0); }} /></Field>
          </div>
          <div className="mt-3 flex justify-end"><SelectInput className="mt-0 max-w-52" value={invoiceSort} onChange={(event) => { setInvoiceSort(event.target.value as typeof invoiceSort); setInvoicePage(0); }}><option value="NEWEST">Newest first</option><option value="OLDEST">Oldest first</option><option value="AMOUNT_HIGH">Highest amount</option><option value="AMOUNT_LOW">Lowest amount</option></SelectInput></div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm"><thead className="bg-slate-50 text-xs uppercase text-slate-500"><tr><th className="px-5 py-3"><input type="checkbox" aria-label="Select all invoices on this page" checked={invoices.length > 0 && invoices.every((invoice) => selectedInvoiceIds.has(invoice.id))} onChange={(event) => setSelectedInvoiceIds(event.target.checked ? new Set(invoices.map((invoice) => invoice.id)) : new Set())} /></th><th>Invoice</th><th>Customer</th><th>Status</th><th>Date</th><th className="text-right">Total</th><th className="px-5 text-right">Actions</th></tr></thead>
            <tbody className="divide-y divide-slate-100">{invoices.map((invoice) => <tr key={invoice.id} className="hover:bg-slate-50"><td className="px-5 py-3"><input type="checkbox" aria-label={`Select invoice ${invoice.invoiceNumber}`} checked={selectedInvoiceIds.has(invoice.id)} onChange={(event) => setSelectedInvoiceIds((current) => { const next = new Set(current); if (event.target.checked) next.add(invoice.id); else next.delete(invoice.id); return next; })} /></td><td><button type="button" onClick={() => void openInvoice(invoice.id)} className="font-bold text-indigo-700 hover:underline">{invoice.invoiceNumber}</button></td><td><p className="font-semibold">{invoice.customerName ?? "Walk-in"}</p><p className="text-xs text-slate-500">{invoice.customerPhone}</p></td><td>{invoice.status.replaceAll("_", " ")}</td><td>{new Date(invoice.completedAt).toLocaleString("en-IN")}</td><td className="text-right font-bold">{money.format(invoice.totalAmount)}</td><td className="px-5 text-right"><button type="button" disabled={invoiceLoadingId !== null} onClick={() => void openInvoice(invoice.id)} className="rounded-lg border border-indigo-200 bg-indigo-50 px-3 py-2 text-xs font-bold text-indigo-700 hover:bg-indigo-100 disabled:opacity-50">{invoiceLoadingId === invoice.id ? "Opening…" : "Open invoice"}</button></td></tr>)}</tbody>
          </table>
          {invoices.length === 0 && <p className="p-6 text-center text-sm text-slate-500">No invoices found.</p>}
        </div>
        <div className="flex items-center justify-between border-t border-slate-200 p-4 text-sm"><span>Page {invoicePages === 0 ? 0 : invoicePage + 1} of {invoicePages}</span><div className="flex gap-2"><button type="button" disabled={invoicePage === 0} onClick={() => setInvoicePage((page) => page - 1)} className="rounded-lg border border-slate-300 px-3 py-2 disabled:opacity-40">Previous</button><button type="button" disabled={invoicePage + 1 >= invoicePages} onClick={() => setInvoicePage((page) => page + 1)} className="rounded-lg border border-slate-300 px-3 py-2 disabled:opacity-40">Next</button></div></div>
      </section>}

      {selectedInvoice && (
        <InvoiceViewer
          invoice={selectedInvoice}
          logoUrl={logoUrl}
          activities={invoiceActivities}
          returnableTotal={invoices.find((invoice) => invoice.id === selectedInvoice.id)?.returnableTotal ?? 0}
          printing={invoicePrinting}
          onClose={() => setSelectedInvoice(null)}
          onPrintReceipt={reprintInvoice}
          onPrintA4={printInvoiceA4}
          onSavePdf={saveInvoicePdf}
          onCopyShare={copyInvoiceShareText}
          onStartReturn={() => { setSelectedInvoice(null); onStartReturn(selectedInvoice.invoiceNumber); }}
        />
      )}
      {selectedInvoice && invoiceOutputMode === "THERMAL" && <ReceiptPrintSurface invoice={selectedInvoice} logoUrl={logoUrl} duplicate />}
      {selectedInvoice && invoiceOutputMode === "A4" && <A4InvoicePrintSurface invoice={selectedInvoice} logoUrl={logoUrl} duplicate />}
      {sales && !selectedInvoice && <PrintableReport report={sales} />}
    </div>
  );
}

function InvoiceViewer({
  invoice,
  logoUrl,
  activities,
  returnableTotal,
  printing,
  onClose,
  onPrintReceipt,
  onPrintA4,
  onSavePdf,
  onCopyShare,
  onStartReturn
}: {
  invoice: PosInvoiceResponse;
  logoUrl: string | null;
  activities: InvoiceActivityResponse[];
  returnableTotal: number;
  printing: boolean;
  onClose: () => void;
  onPrintReceipt: () => Promise<void>;
  onPrintA4: () => Promise<void>;
  onSavePdf: () => Promise<void>;
  onCopyShare: () => Promise<void>;
  onStartReturn: () => void;
}) {
  const reprintCount = activities.filter((activity) => activity.eventType.includes("PRINTED")).length;
  const canReturn = returnableTotal > 0 && invoice.status !== "RETURNED" && invoice.status !== "CANCELLED";
  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-slate-950/70 p-6" role="dialog" aria-modal="true" aria-label={`Invoice ${invoice.invoiceNumber}`}>
      <div className="flex max-h-[94vh] w-full max-w-6xl flex-col rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-200 p-5">
          <div><div className="flex items-center gap-2"><p className="text-sm font-bold text-indigo-700">Saved invoice</p><span className="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold uppercase">{invoice.status.replaceAll("_", " ")}</span></div><h3 className="text-xl font-bold">Invoice {invoice.invoiceNumber}</h3><p className="mt-1 text-xs text-slate-500">{new Date(invoice.completedAt).toLocaleString("en-IN")} · {money.format(invoice.totals.totalAmount)} · {reprintCount} print{reprintCount === 1 ? "" : "s"} recorded</p></div>
          <button type="button" onClick={onClose} disabled={printing} aria-label="Close invoice" className="rounded-lg px-3 py-2 font-bold hover:bg-slate-100 disabled:opacity-50">✕</button>
        </div>
        <div className="grid min-h-0 flex-1 grid-cols-[minmax(380px,1fr)_360px] overflow-hidden">
          <div className="min-h-0 overflow-auto bg-slate-100 p-6"><div className="mx-auto w-fit shadow-xl"><ThermalReceipt invoice={invoice} logoUrl={logoUrl} duplicate /></div></div>
          <aside className="min-h-0 overflow-auto border-l border-slate-200 p-5">
            <h4 className="font-bold">Invoice activities</h4>
            <p className="mt-1 text-xs text-slate-500">Actions are retained against the original invoice.</p>
            <div className="mt-4 space-y-2">
              {activities.length === 0 ? <p className="rounded-lg bg-slate-50 p-3 text-xs text-slate-500">No activity is available.</p> : activities.map((activity, index) => (
                <div key={`${activity.eventType}-${activity.occurredAt}-${index}`} className="rounded-lg border border-slate-200 p-3">
                  <p className="text-xs font-bold">{invoiceActivityLabel(activity.eventType)}</p>
                  <p className="mt-1 text-[11px] text-slate-500">{activity.actorName} · {new Date(activity.occurredAt).toLocaleString("en-IN")}</p>
                </div>
              ))}
            </div>
          </aside>
        </div>
        <div className="flex items-center justify-between gap-3 border-t border-slate-200 p-5">
          <div><p className="text-xs font-bold text-slate-700">Returnable: {money.format(returnableTotal)}</p><p className="text-[11px] text-slate-500">Thermal and A4 reprints are marked as duplicate copies.</p></div>
          <div className="flex flex-wrap justify-end gap-2">
            <button type="button" onClick={onStartReturn} disabled={printing || !canReturn} className="rounded-lg border border-red-300 px-3 py-2.5 text-sm font-bold text-red-700 disabled:opacity-40">Return / cancel</button>
            <button type="button" onClick={() => void onCopyShare()} disabled={printing} className="rounded-lg border border-slate-300 px-3 py-2.5 text-sm font-bold disabled:opacity-50">Copy to share</button>
            <button type="button" onClick={() => void onSavePdf()} disabled={printing} className="rounded-lg border border-slate-300 px-3 py-2.5 text-sm font-bold disabled:opacity-50">Save PDF</button>
            <button type="button" onClick={() => void onPrintA4()} disabled={printing} className="rounded-lg border border-indigo-300 px-3 py-2.5 text-sm font-bold text-indigo-700 disabled:opacity-50">Print A4</button>
            <button type="button" onClick={() => void onPrintReceipt()} disabled={printing} className="rounded-lg bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-50">{printing ? "Working…" : "Reprint receipt"}</button>
          </div>
        </div>
      </div>
    </div>
  );
}

function invoiceActivityLabel(eventType: string) {
  const labels: Record<string, string> = {
    SALE_COMPLETED: "Sale completed",
    SALE_RETURNED: "Items returned",
    SALE_CANCELLED: "Invoice cancelled",
    INVOICE_THERMAL_REPRINTED: "Thermal receipt reprinted",
    INVOICE_A4_PRINTED: "A4 invoice printed",
    INVOICE_PDF_EXPORTED: "PDF exported",
    INVOICE_SHARE_COPIED: "Share summary copied"
  };
  return labels[eventType] ?? eventType.replaceAll("_", " ").toLowerCase();
}

function A4InvoicePrintSurface({ invoice, logoUrl, duplicate }: { invoice: PosInvoiceResponse; logoUrl: string | null; duplicate: boolean }) {
  return <><style>{"@media print { @page { size: A4 portrait; margin: 12mm; } }"}</style><div className="report-print-surface" aria-hidden="true"><A4Invoice invoice={invoice} logoUrl={logoUrl} duplicate={duplicate} /></div></>;
}

function A4Invoice({ invoice, logoUrl, duplicate }: { invoice: PosInvoiceResponse; logoUrl: string | null; duplicate: boolean }) {
  const customer = invoice.payments.find((payment) => payment.customerName);
  const template = invoice.store.a4InvoiceTemplate ?? "MODERN";
  const palette = template === "CLASSIC"
    ? { primary: "#102a43", accent: "#d4a72c", soft: "#fff8e6", label: "Classic Premium" }
    : template === "MINIMAL"
      ? { primary: "#0f766e", accent: "#2dd4bf", soft: "#ecfdf5", label: "Minimal Fresh" }
      : { primary: "#4338ca", accent: "#38bdf8", soft: "#eef2ff", label: "Modern Color" };
  return (
    <article className="a4-invoice overflow-hidden rounded-xl border" style={{ borderColor: palette.primary }}>
      <div className="h-3" style={{ background: `linear-gradient(90deg, ${palette.primary}, ${palette.accent})` }} />
      <header className="report-print-header flex items-start justify-between p-6 text-white" style={{ backgroundColor: palette.primary }}>
        <div className="flex items-start gap-4">{logoUrl && <span className="grid h-20 w-24 place-items-center rounded-lg bg-white p-2"><img src={logoUrl} alt="" className="max-h-full max-w-full object-contain" /></span>}<div><p className="text-xs font-bold uppercase tracking-[0.2em]" style={{ color: palette.accent }}>{palette.label}</p><h1 className="mt-1 text-2xl font-black text-white">{invoice.store.shopName}</h1><p className="text-white/90">{invoice.store.address}</p><p className="text-white/90">Phone: {invoice.store.phone}{invoice.store.gstin ? ` · GSTIN: ${invoice.store.gstin}` : ""}</p></div></div>
        <div className="text-right"><p className="text-xs font-bold uppercase tracking-widest" style={{ color: palette.accent }}>Tax invoice</p><h2 className="mt-1 text-xl font-black text-white">{invoice.invoiceNumber}</h2>{duplicate && <p className="mt-2 rounded border border-white px-2 py-1 text-xs font-black tracking-widest text-white">DUPLICATE COPY</p>}</div>
      </header>
      <div className="p-6">
        <section className="grid grid-cols-2 gap-6 rounded-lg p-4 text-sm" style={{ backgroundColor: palette.soft }}><div><p className="text-xs font-bold uppercase" style={{ color: palette.primary }}>Invoice details</p><p className="mt-1">Date: {new Date(invoice.completedAt).toLocaleString("en-IN")}</p><p>Cashier ID: {invoice.cashierUserId}</p><p>Status: {invoice.status.replaceAll("_", " ")}</p></div><div><p className="text-xs font-bold uppercase" style={{ color: palette.primary }}>Bill to</p><p className="mt-1 font-bold">{customer?.customerName ?? "Walk-in customer"}</p>{customer?.customerPhone && <p>{customer.customerPhone}</p>}</div></section>
        <table className="report-detail-table mt-6"><thead style={{ backgroundColor: palette.primary, color: "white" }}><tr><th>#</th><th className="text-left">Item</th><th>Qty</th><th>Rate</th><th>Tax</th><th className="text-right">Amount</th></tr></thead><tbody>{invoice.totals.lines.map((line) => <tr key={line.lineNumber}><td>{line.lineNumber}</td><td>{line.name}</td><td>{line.quantity}</td><td>{money.format(line.unitPrice)}</td><td>{invoice.totals.gstApplied ? `${line.gstRate}%` : "—"}</td><td className="text-right">{money.format(line.lineTotal)}</td></tr>)}</tbody></table>
        <section className="ml-auto mt-6 w-80 overflow-hidden rounded-lg border text-sm" style={{ borderColor: palette.primary }}><div className="space-y-1 p-4"><A4Total label="Subtotal" value={invoice.totals.subtotalAmount} /><A4Total label="Discount" value={-(invoice.totals.lineDiscountAmount + invoice.totals.billDiscountAmount)} />{invoice.totals.gstApplied && (invoice.totals.taxMode === "INTRA_STATE" ? <><A4Total label="CGST" value={invoice.totals.cgstAmount} /><A4Total label="SGST" value={invoice.totals.sgstAmount} /></> : <A4Total label="IGST" value={invoice.totals.igstAmount} />)}<A4Total label="Round off" value={invoice.totals.roundOffAmount} /></div><div className="flex justify-between px-4 py-3 text-lg font-black text-white" style={{ backgroundColor: palette.primary }}><span>Total</span><span>{money.format(invoice.totals.totalAmount)}</span></div></section>
        <section className="mt-6 rounded-lg border-l-4 p-4" style={{ borderColor: palette.accent, backgroundColor: palette.soft }}><h3 className="text-sm font-black" style={{ color: palette.primary }}>Payment details</h3>{invoice.payments.map((payment, index) => <p key={index} className="text-sm">{payment.mode}: {money.format(payment.amount)}{payment.reference ? ` · Ref ${payment.reference}` : ""}</p>)}</section>
        {invoice.notes && <p className="mt-5 text-sm"><strong>Notes:</strong> {invoice.notes}</p>}
        <footer className="report-print-footer mt-10 border-t pt-3 text-center" style={{ borderColor: palette.accent, color: palette.primary }}>Computer-generated invoice · Thank you for your business</footer>
      </div>
    </article>
  );
}

function A4Total({ label, value }: { label: string; value: number }) {
  return <div className="flex justify-between"><span>{label}</span><span>{money.format(value)}</span></div>;
}

function MetricCard({
  label,
  value,
  tone
}: {
  label: string;
  value: string;
  tone: "indigo" | "slate" | "green" | "red";
}) {
  const tones = {
    indigo: "border-indigo-200 bg-indigo-50 text-indigo-800",
    slate: "border-slate-200 bg-white text-slate-900",
    green: "border-emerald-200 bg-emerald-50 text-emerald-800",
    red: "border-red-200 bg-red-50 text-red-800"
  };
  return (
    <article className={`rounded-2xl border p-5 shadow-sm ${tones[tone]}`}>
      <p className="text-xs font-bold uppercase tracking-wider opacity-70">{label}</p>
      <p className="mt-3 text-2xl font-black tracking-tight">{value}</p>
    </article>
  );
}

function PaymentBreakdown({ summary }: { summary: SalesSummaryResponse }) {
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
      <h4 className="font-bold">Today's payment mix</h4>
      <div className="mt-4 space-y-3">
        {(Object.keys(paymentLabels) as PaymentMode[]).map((mode) => (
          <div key={mode} className="flex items-center justify-between text-sm">
            <span className="font-semibold text-slate-500">{paymentLabels[mode]}</span>
            <span className={`font-bold ${mode === "UDHAAR" ? "text-red-700" : "text-slate-900"}`}>
              {money.format(summary.paymentTotals[mode] ?? 0)}
            </span>
          </div>
        ))}
      </div>
    </article>
  );
}

function AlertCard({
  title,
  count,
  items,
  tone
}: {
  title: string;
  count: number;
  items: ReportStockAlertResponse[];
  tone: "amber" | "red";
}) {
  const color = tone === "red" ? "text-red-700 bg-red-50" : "text-amber-800 bg-amber-50";
  return (
    <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
      <div className="flex items-center justify-between border-b border-slate-100 p-5">
        <h4 className="font-bold">{title}</h4>
        <span className={`rounded-full px-3 py-1 text-xs font-black ${color}`}>{count}</span>
      </div>
      <div className="divide-y divide-slate-100">
        {items.length === 0 ? (
          <p className="p-5 text-sm text-emerald-700">No items need attention.</p>
        ) : items.map((item) => (
          <div key={item.productId} className="flex items-center justify-between px-5 py-3 text-sm">
            <div className="min-w-0">
              <p className="truncate font-semibold">{item.name}</p>
              <p className="text-xs text-slate-500">{item.sku}</p>
            </div>
            <span className="ml-3 font-bold text-slate-700">{item.stockQuantity} {item.unit}</span>
          </div>
        ))}
      </div>
    </article>
  );
}

function RevenueTrend({ days }: { days: DashboardReportResponse["revenueTrend"] }) {
  const recent = days.slice(-14);
  const maximum = Math.max(...recent.map((day) => Math.max(0, day.totalSales)), 1);
  return <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
    <div className="flex items-center justify-between"><h4 className="font-bold">Revenue trend</h4><span className="text-xs text-slate-500">Last {recent.length} active days</span></div>
    <div className="mt-5 flex h-40 items-end gap-2">{recent.length === 0 ? <p className="m-auto text-sm text-slate-500">No sales yet.</p> : recent.map((day) => <div key={day.businessDate} className="group flex min-w-0 flex-1 flex-col items-center justify-end gap-2" title={`${day.businessDate}: ${money.format(day.totalSales)}`}><span className="hidden text-[9px] font-bold group-hover:block">{money.format(day.totalSales)}</span><div className="w-full rounded-t-md bg-indigo-500" style={{ height: `${Math.max(4, day.totalSales / maximum * 120)}px` }} /><span className="text-[9px] text-slate-500">{day.businessDate.slice(8)}</span></div>)}</div>
  </article>;
}

function TopProducts({ products }: { products: DashboardReportResponse["topSellingProducts"] }) {
  return <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"><div className="border-b border-slate-100 p-5"><h4 className="font-bold">Top-selling products</h4><p className="text-xs text-slate-500">Net of returns · 30 days</p></div><div className="divide-y divide-slate-100">{products.length === 0 ? <p className="p-5 text-sm text-slate-500">No product sales yet.</p> : products.slice(0, 6).map((product, index) => <div key={product.productId} className="flex items-center gap-3 px-5 py-3"><span className="grid h-7 w-7 place-items-center rounded-full bg-indigo-50 text-xs font-black text-indigo-700">{index + 1}</span><div className="min-w-0 flex-1"><p className="truncate text-sm font-bold">{product.productName}</p><p className="text-xs text-slate-500">{product.quantity} sold</p></div><strong className="text-sm">{money.format(product.netSales)}</strong></div>)}</div></article>;
}

function RecentTransactions({ transactions }: { transactions: DashboardReportResponse["recentTransactions"] }) {
  return <article className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"><div className="border-b border-slate-100 p-5"><h4 className="font-bold">Recent transactions</h4><p className="text-xs text-slate-500">Sales, returns and cancellations</p></div><div className="divide-y divide-slate-100">{transactions.length === 0 ? <p className="p-5 text-sm text-slate-500">No transactions yet.</p> : transactions.slice(0, 6).map((transaction) => <div key={transaction.id} className="flex items-center justify-between px-5 py-3"><div className="min-w-0"><p className="truncate text-sm font-bold">{transaction.referenceNumber}</p><p className="text-xs text-slate-500">{transaction.customerName ?? transaction.type} · {new Date(transaction.occurredAt).toLocaleDateString("en-IN")}</p></div><strong className={transaction.amount >= 0 ? "text-emerald-700" : "text-red-700"}>{money.format(transaction.amount)}</strong></div>)}</div></article>;
}

function SalesReportBody({ report }: { report: SalesReportResponse }) {
  const maxSales = Math.max(...report.dailySales.map((day) => day.totalSales), 1);
  const marginRate = report.summary.totalSales === 0
    ? 0
    : (report.summary.grossMargin / report.summary.totalSales) * 100;
  return (
    <div>
      <div className="grid grid-cols-5 gap-px bg-slate-200">
        <ReportMetric label="Bills" value={String(report.summary.billCount)} />
        <ReportMetric label="Sales" value={money.format(report.summary.totalSales)} />
        <ReportMetric label="Discount" value={money.format(report.summary.discountAmount)} />
        <ReportMetric label="GST" value={money.format(report.summary.totalTax)} />
        <ReportMetric label="Margin" value={`${money.format(report.summary.grossMargin)} · ${marginRate.toFixed(1)}%`} />
      </div>
      <div className="overflow-x-auto p-6">
        <div className="grid min-w-[850px] grid-cols-[150px_90px_1fr_150px_150px] px-3 pb-3 text-xs font-bold uppercase tracking-wider text-slate-500">
          <span>Business date</span><span className="text-right">Bills</span><span>Sales activity</span>
          <span className="text-right">Cost snapshot</span><span className="text-right">Gross margin</span>
        </div>
        {report.dailySales.length === 0 ? (
          <p className="border-t border-slate-100 px-3 py-8 text-center text-sm text-slate-500">No completed sales in this period.</p>
        ) : report.dailySales.map((day) => (
          <div key={day.businessDate} className="grid min-w-[850px] grid-cols-[150px_90px_1fr_150px_150px] items-center border-t border-slate-100 px-3 py-3 text-sm">
            <span className="font-semibold">{day.businessDate}</span>
            <span className="text-right">{day.billCount}</span>
            <div className="mx-5 flex items-center gap-3">
              <div className="h-2 flex-1 overflow-hidden rounded-full bg-slate-100">
                <div className="h-full rounded-full bg-indigo-500" style={{ width: `${Math.max(2, (day.totalSales / maxSales) * 100)}%` }} />
              </div>
              <span className="w-28 text-right font-bold">{money.format(day.totalSales)}</span>
            </div>
            <span className="text-right text-slate-600">{money.format(day.snapshotCost)}</span>
            <span className={`text-right font-bold ${day.grossMargin >= 0 ? "text-emerald-700" : "text-red-700"}`}>
              {money.format(day.grossMargin)}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function ReportMetric({ label, value }: { label: string; value: string }) {
  return <div className="bg-slate-50 p-4"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">{label}</p><p className="mt-2 font-black">{value}</p></div>;
}

function PrintableReport({ report }: { report: SalesReportResponse }) {
  return (
    <>
      <style>{"@media print { @page { size: A4 portrait; margin: 12mm; } }"}</style>
      <div className="report-print-surface" aria-hidden="true">
        <div className="report-print-header">
          <h1>{report.shopName}</h1>
          <h2>Sales & Gross Margin Report</h2>
          <p>{report.from} to {report.to} · {report.timezone}</p>
        </div>
        <table className="report-summary-table">
          <tbody>
            <tr><th>Bills</th><td>{report.summary.billCount}</td><th>Total sales</th><td>{money.format(report.summary.totalSales)}</td></tr>
            <tr><th>Discount</th><td>{money.format(report.summary.discountAmount)}</td><th>Total GST</th><td>{money.format(report.summary.totalTax)}</td></tr>
            <tr><th>Snapshot cost</th><td>{money.format(report.summary.snapshotCost)}</td><th>Gross margin</th><td>{money.format(report.summary.grossMargin)}</td></tr>
          </tbody>
        </table>
        <h3>Daily breakdown</h3>
        <table className="report-detail-table">
          <thead><tr><th>Date</th><th>Bills</th><th>Sales</th><th>Cost</th><th>Margin</th></tr></thead>
          <tbody>
            {report.dailySales.map((day) => (
              <tr key={day.businessDate}><td>{day.businessDate}</td><td>{day.billCount}</td><td>{money.format(day.totalSales)}</td><td>{money.format(day.snapshotCost)}</td><td>{money.format(day.grossMargin)}</td></tr>
            ))}
          </tbody>
        </table>
        <h3>Payment breakdown</h3>
        <table className="report-detail-table compact">
          <tbody>
            {(Object.keys(paymentLabels) as PaymentMode[]).map((mode) => (
              <tr key={mode}><th>{paymentLabels[mode]}</th><td>{money.format(report.summary.paymentTotals[mode] ?? 0)}</td></tr>
            ))}
          </tbody>
        </table>
        <p className="report-print-footer">Generated {new Date(report.generatedAt).toLocaleString("en-IN")} · Gross margin = sales − invoice purchase-cost snapshots.</p>
      </div>
    </>
  );
}
