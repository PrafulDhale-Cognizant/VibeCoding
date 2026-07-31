import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type {
  DashboardReportResponse,
  PaymentMode,
  ReportStockAlertResponse,
  SalesReportResponse,
  SalesSummaryResponse
} from "../../types";
import { ErrorNotice, Field, TextInput } from "../FormControls";

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

export function ReportsPanel({ accessToken }: { accessToken: string }) {
  const initial = useMemo(initialRange, []);
  const [dashboard, setDashboard] = useState<DashboardReportResponse | null>(null);
  const [sales, setSales] = useState<SalesReportResponse | null>(null);
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [loading, setLoading] = useState(true);
  const [reportLoading, setReportLoading] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState("");

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

      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-sm font-semibold text-slate-500">
            {dashboard ? `${dashboard.businessDate} · ${dashboard.timezone}` : "Current business day"}
          </p>
          <h3 className="mt-1 text-2xl font-bold">Today at a glance</h3>
        </div>
        <button
          type="button"
          onClick={() => void refreshDashboard()}
          disabled={loading}
          className="rounded-lg border border-slate-300 bg-white px-4 py-2.5 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-50"
        >
          {loading ? "Refreshing…" : "Refresh dashboard"}
        </button>
      </div>

      {dashboard && (
        <>
          <section className="grid grid-cols-4 gap-4">
            <MetricCard label="Today's sales" value={money.format(dashboard.today.totalSales)} tone="indigo" />
            <MetricCard label="Bills completed" value={String(dashboard.today.billCount)} tone="slate" />
            <MetricCard label="Gross margin" value={money.format(dashboard.today.grossMargin)} tone="green" />
            <MetricCard label="Khata outstanding" value={money.format(dashboard.credit.totalOutstanding)} tone="red" />
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

      {sales && <PrintableReport report={sales} />}
    </div>
  );
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
