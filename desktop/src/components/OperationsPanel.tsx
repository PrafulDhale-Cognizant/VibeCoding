import { useEffect, useState } from "react";
import { ErrorNotice, Field, SuccessNotice, TextInput } from "./FormControls";

type Diagnostics = Awaited<ReturnType<NonNullable<Window["billingDesktop"]>["getDiagnostics"]>>;
type Printer = Awaited<ReturnType<NonNullable<Window["billingDesktop"]>["listPrinters"]>>[number];

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

function bytes(value: number) {
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: 1 }).format(value / 1024 / 1024 / 1024) + " GB";
}

export function OperationsPanel() {
  const [diagnostics, setDiagnostics] = useState<Diagnostics | null>(null);
  const [printers, setPrinters] = useState<Printer[]>([]);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [busy, setBusy] = useState(false);
  const [inactivityMinutes, setInactivityMinutes] = useState(() =>
    Number(localStorage.getItem("simplified-billing.security.inactivity-minutes") ?? "15"));

  const refresh = async () => {
    if (!window.billingDesktop) return;
    const [details, availablePrinters] = await Promise.all([
      window.billingDesktop.getDiagnostics(), window.billingDesktop.listPrinters()
    ]);
    setDiagnostics(details); setPrinters(availablePrinters);
  };

  useEffect(() => { void refresh().catch((caught) => setError(messageFrom(caught, "Diagnostics could not be loaded."))); }, []);

  async function action(work: () => Promise<unknown>, completed: string) {
    setBusy(true); setError(""); setSuccess("");
    try { const result = await work(); if (result) setSuccess(completed); await refresh(); }
    catch (caught) { setError(messageFrom(caught, "The operation failed.")); }
    finally { setBusy(false); }
  }

  if (!window.billingDesktop) return <ErrorNotice message="Desktop operations are available in the installed Electron application." />;

  return <div className="mx-auto max-w-6xl space-y-6">
    {error && <ErrorNotice message={error} />}{success && <SuccessNotice message={success} />}
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-start justify-between"><div><h3 className="text-xl font-black">System diagnostics</h3>
        <p className="mt-1 text-sm text-slate-500">Local application, database, disk, printer and backup readiness.</p></div>
        <button disabled={busy} onClick={() => void action(() => window.billingDesktop!.exportSupportBundle(), "Sanitized support bundle exported.")} className="rounded-xl border border-slate-300 px-4 py-2 font-bold">Export support bundle</button></div>
      {diagnostics && <div className="mt-6 grid grid-cols-3 gap-4 text-sm">
        <Status label="Application" value={`v${diagnostics.applicationVersion} · ${diagnostics.packaged ? "Installed" : "Development"}`} good />
        <Status label="Backend lifecycle" value={diagnostics.backendManaged ? "Managed by desktop" : "External / unavailable"} good={diagnostics.backendManaged} />
        <Status label="Backend package" value={diagnostics.backendJarPresent ? "Present" : "Not packaged"} good={diagnostics.backendJarPresent} />
        <Status label="Database" value={`${diagnostics.database.database} · ${diagnostics.database.host}:${diagnostics.database.port}`} good />
        <Status label="Free disk" value={diagnostics.disk ? bytes(diagnostics.disk.freeBytes) : "Unavailable"} good={Boolean(diagnostics.disk && diagnostics.disk.freeBytes > 1024 ** 3)} />
        <Status label="Application log" value={diagnostics.logFilePresent ? "Available" : "Not created yet"} good={diagnostics.logFilePresent} />
      </div>}
    </section>

    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h3 className="text-xl font-black">Encrypted backup & restore</h3>
      <p className="mt-1 text-sm text-slate-500">Backups use AES-256-GCM encryption. Store the password safely; it cannot be recovered.</p>
      <div className="mt-5 max-w-lg"><Field label="Backup password (minimum 12 characters)"><TextInput type="password" minLength={12} value={password} onChange={(event) => setPassword(event.target.value)} /></Field></div>
      {diagnostics?.backup && <div className={`mt-4 rounded-xl p-4 text-sm ${diagnostics.backup.successful ? "bg-green-50 text-green-800" : "bg-red-50 text-red-800"}`}>
        {diagnostics.backup.successful ? `Last backup: ${new Date(diagnostics.backup.createdAt!).toLocaleString("en-IN")} · ${diagnostics.backup.fileName}` : `Last backup failed: ${diagnostics.backup.message}`}
      </div>}
      <div className="mt-5 flex gap-3"><button disabled={busy || password.length < 12} onClick={() => void action(() => window.billingDesktop!.createBackup(password), "Encrypted backup created and verified.")} className="rounded-xl bg-indigo-700 px-5 py-3 font-bold text-white disabled:opacity-40">Back up now</button>
        <button disabled={busy || password.length < 12 || !diagnostics?.packaged} onClick={() => {
          if (window.confirm("Restore replaces the current database after creating a pre-restore backup. Continue?")) void action(() => window.billingDesktop!.restoreBackup(password), "Backup restored; the local service is restarting.");
        }} className="rounded-xl border border-red-300 px-5 py-3 font-bold text-red-700 disabled:opacity-40">Restore backup</button></div>
    </section>

    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"><h3 className="text-xl font-black">Signed offline update</h3>
      <p className="mt-1 text-sm text-slate-500">Select an installer accompanied by its signed JSON manifest and signature file. A verified encrypted backup is created before installation.</p>
      <button disabled={busy || password.length < 12 || !diagnostics?.packaged} onClick={() => {
        if (window.confirm("Verify the update, create a pre-update backup, and close the application to install it?")) void action(() => window.billingDesktop!.applyOfflineUpdate(password), "Update verified and started.");
      }} className="mt-5 rounded-xl border border-indigo-300 px-5 py-3 font-bold text-indigo-700 disabled:opacity-40">Install offline update</button>
    </section>

    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"><h3 className="text-xl font-black">Printers</h3>
      <p className="mt-1 text-sm text-slate-500">Printers discovered through the operating system.</p>
      <div className="mt-5 divide-y rounded-xl border border-slate-200">{printers.length === 0 ? <p className="p-4 text-sm text-slate-500">No printers detected.</p> : printers.map((printer) => <div key={printer.name} className="flex items-center justify-between p-4"><div><p className="font-bold">{printer.displayName || printer.name}</p><p className="text-xs text-slate-500">{printer.isDefault ? "Default printer" : `Status ${printer.status}`}</p></div>
        <button disabled={busy} onClick={() => void action(() => window.billingDesktop!.testPrinter(printer.name), `Test page sent to ${printer.displayName || printer.name}.`)} className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-bold">Test print</button></div>)}</div>
    </section>
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"><h3 className="text-xl font-black">Automatic lock</h3>
      <p className="mt-1 text-sm text-slate-500">The application returns to sign-in after this period without keyboard, mouse or touch activity.</p>
      <div className="mt-4 flex items-center gap-3"><select value={inactivityMinutes} onChange={(event) => {
        const value = Number(event.target.value); setInactivityMinutes(value);
        localStorage.setItem("simplified-billing.security.inactivity-minutes", String(value));
      }} className="rounded-lg border border-slate-300 bg-white px-4 py-2">
        <option value={5}>5 minutes</option><option value={10}>10 minutes</option><option value={15}>15 minutes</option>
        <option value={30}>30 minutes</option><option value={60}>60 minutes</option><option value={0}>Disabled</option>
      </select><span className="text-sm text-slate-500">Applies from the next sign-in.</span></div>
    </section>
  </div>;
}

function Status({ label, value, good }: { label: string; value: string; good: boolean }) {
  return <div className="rounded-xl bg-slate-50 p-4"><div className="flex items-center gap-2"><span className={`h-2.5 w-2.5 rounded-full ${good ? "bg-green-500" : "bg-amber-500"}`} /><p className="text-xs font-bold uppercase text-slate-500">{label}</p></div><p className="mt-2 font-bold">{value}</p></div>;
}
