import { useEffect, useMemo, useState, type FormEvent } from "react";
import { api } from "../../lib/api";
import type { SupplierAnalyticsResponse } from "../../types";
import { ErrorNotice, Field, TextInput } from "../FormControls";

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });

function isoDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function initialRange() {
  const current = new Date();
  return {
    from: isoDate(new Date(current.getFullYear(), current.getMonth(), 1)),
    to: isoDate(current)
  };
}

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

export function SupplierAnalyticsPanel({ accessToken }: { accessToken: string }) {
  const initial = useMemo(initialRange, []);
  const [from, setFrom] = useState(initial.from);
  const [to, setTo] = useState(initial.to);
  const [report, setReport] = useState<SupplierAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState("");

  async function load(rangeFrom: string, rangeTo: string) {
    setLoading(true);
    setError("");
    try {
      setReport(await api.getSupplierAnalytics(accessToken, rangeFrom, rangeTo));
    } catch (caught) {
      setError(messageFrom(caught, "Supplier analytics could not be loaded."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(initial.from, initial.to); }, [accessToken, initial.from, initial.to]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    await load(from, to);
  }

  async function printReport() {
    if (!report) return;
    setPrinting(true);
    setError("");
    try {
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      if (window.billingDesktop?.printReport) await window.billingDesktop.printReport();
      else window.print();
    } catch (caught) {
      setError(messageFrom(caught, "Supplier analytics could not be printed."));
    } finally {
      setPrinting(false);
    }
  }

  function exportCsv() {
    if (!report) return;
    const rows: Array<Array<string | number>> = [
      ["Simplified Billing - Supplier Analytics"],
      ["From", report.from, "To", report.to, "Timezone", report.timezone],
      [],
      ["Supplier", "Purchases", "Returns", "Net purchases", "Payments", "Current payable", "Supplier credit"],
      ...report.suppliers.map((row) => [
        row.supplierName,
        row.purchaseTotal.toFixed(2),
        row.returnTotal.toFixed(2),
        row.netPurchaseTotal.toFixed(2),
        row.paymentTotal.toFixed(2),
        row.outstandingAmount.toFixed(2),
        row.creditAmount.toFixed(2)
      ]),
      [],
      ["Totals", report.purchaseTotal.toFixed(2), report.returnTotal.toFixed(2),
        report.netPurchaseTotal.toFixed(2), report.paymentTotal.toFixed(2),
        report.totalOutstanding.toFixed(2), report.totalCredit.toFixed(2)]
    ];
    const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob(["\ufeff", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = `supplier-analytics-${report.from}-to-${report.to}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="space-y-5">
      {error && <ErrorNotice message={error} />}
      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex items-end justify-between gap-5 border-b border-slate-200 p-5">
          <form onSubmit={submit} className="flex items-end gap-4">
            <Field label="From"><TextInput required type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></Field>
            <Field label="To"><TextInput required type="date" value={to} onChange={(event) => setTo(event.target.value)} /></Field>
            <button disabled={loading} className="rounded-xl bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white disabled:opacity-50">{loading ? "Loading…" : "Run analytics"}</button>
          </form>
          <div className="flex gap-3">
            <button type="button" disabled={!report} onClick={exportCsv} className="rounded-xl border border-slate-300 px-4 py-2.5 text-sm font-bold disabled:opacity-40">Export CSV</button>
            <button type="button" disabled={!report || printing} onClick={() => void printReport()} className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-bold text-white disabled:opacity-40">{printing ? "Opening print…" : "Print report"}</button>
          </div>
        </div>

        {report ? (
          <div>
            <section className="grid grid-cols-6 gap-px bg-slate-200">
              <Metric label="Purchases" value={money.format(report.purchaseTotal)} />
              <Metric label="Returns" value={money.format(report.returnTotal)} tone="amber" />
              <Metric label="Net purchases" value={money.format(report.netPurchaseTotal)} />
              <Metric label="Payments" value={money.format(report.paymentTotal)} />
              <Metric label="Current payable" value={money.format(report.totalOutstanding)} tone="red" />
              <Metric label="Supplier credit" value={money.format(report.totalCredit)} tone="indigo" />
            </section>
            <div className="overflow-x-auto p-5">
              <div className="grid min-w-[1000px] grid-cols-[1fr_repeat(6,150px)] bg-slate-50 px-4 py-3 text-xs font-bold uppercase tracking-wider text-slate-500"><span>Supplier</span><span className="text-right">Purchases</span><span className="text-right">Returns</span><span className="text-right">Net</span><span className="text-right">Payments</span><span className="text-right">Payable</span><span className="text-right">Credit</span></div>
              {report.suppliers.length === 0 ? <p className="p-8 text-center text-sm text-slate-500">No supplier activity or open balances for this period.</p> : report.suppliers.map((row) => <div key={row.supplierId} className="grid min-w-[1000px] grid-cols-[1fr_repeat(6,150px)] border-t border-slate-100 px-4 py-3 text-sm"><strong>{row.supplierName}</strong><span className="text-right">{money.format(row.purchaseTotal)}</span><span className="text-right text-amber-700">{money.format(row.returnTotal)}</span><span className="text-right font-bold">{money.format(row.netPurchaseTotal)}</span><span className="text-right">{money.format(row.paymentTotal)}</span><span className="text-right font-bold text-red-700">{money.format(row.outstandingAmount)}</span><span className="text-right font-bold text-indigo-700">{money.format(row.creditAmount)}</span></div>)}
            </div>
          </div>
        ) : <p className="p-6 text-sm text-slate-500">Run a date range to view supplier analytics.</p>}
      </section>
      {report && <PrintableSupplierAnalytics report={report} />}
    </div>
  );
}

function Metric({ label, value, tone = "slate" }: { label: string; value: string; tone?: "slate" | "amber" | "red" | "indigo" }) {
  const colors = { slate: "text-slate-900", amber: "text-amber-800", red: "text-red-700", indigo: "text-indigo-700" };
  return <article className="bg-white p-4"><p className="text-xs font-bold uppercase tracking-wider text-slate-500">{label}</p><p className={`mt-2 font-black ${colors[tone]}`}>{value}</p></article>;
}

function PrintableSupplierAnalytics({ report }: { report: SupplierAnalyticsResponse }) {
  return (
    <>
      <style>{"@media print { @page { size: A4 landscape; margin: 10mm; } }"}</style>
      <div className="report-print-surface" aria-hidden="true">
        <div className="report-print-header"><h1>Supplier Purchase & Payable Analytics</h1><p>{report.from} to {report.to} · {report.timezone}</p></div>
        <table className="report-summary-table"><tbody><tr><th>Purchases</th><td>{money.format(report.purchaseTotal)}</td><th>Returns</th><td>{money.format(report.returnTotal)}</td><th>Net purchases</th><td>{money.format(report.netPurchaseTotal)}</td></tr><tr><th>Payments</th><td>{money.format(report.paymentTotal)}</td><th>Current payable</th><td>{money.format(report.totalOutstanding)}</td><th>Supplier credit</th><td>{money.format(report.totalCredit)}</td></tr></tbody></table>
        <h3>Supplier breakdown</h3>
        <table className="report-detail-table"><thead><tr><th>Supplier</th><th>Purchases</th><th>Returns</th><th>Net</th><th>Payments</th><th>Payable</th><th>Credit</th></tr></thead><tbody>{report.suppliers.map((row) => <tr key={row.supplierId}><td>{row.supplierName}</td><td>{money.format(row.purchaseTotal)}</td><td>{money.format(row.returnTotal)}</td><td>{money.format(row.netPurchaseTotal)}</td><td>{money.format(row.paymentTotal)}</td><td>{money.format(row.outstandingAmount)}</td><td>{money.format(row.creditAmount)}</td></tr>)}</tbody></table>
        <p className="report-print-footer">Generated {new Date(report.generatedAt).toLocaleString("en-IN")} · Current balances are as of generation time.</p>
      </div>
    </>
  );
}
