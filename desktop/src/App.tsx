import { useBackendHealth } from "./hooks/useBackendHealth";

const milestones = [
  {
    name: "Desktop foundation",
    description: "Secure Electron shell, React UI and local Spring Boot connectivity.",
    status: "complete"
  },
  {
    name: "Store setup and authentication",
    description: "First-run shop configuration, local users, roles and JWT sessions.",
    status: "next"
  },
  {
    name: "Inventory and purchases",
    description: "Catalog, barcodes, stock ledger, suppliers and purchase receipt.",
    status: "planned"
  },
  {
    name: "POS and billing",
    description: "Scanner-first cart, atomic checkout, payments and thermal printing.",
    status: "planned"
  }
] as const;

function StatusBadge({ status }: { status: "complete" | "next" | "planned" }) {
  const styles = {
    complete: "bg-emerald-100 text-emerald-800 ring-emerald-600/20",
    next: "bg-indigo-100 text-indigo-800 ring-indigo-600/20",
    planned: "bg-slate-100 text-slate-700 ring-slate-500/20"
  };

  return (
    <span
      className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold capitalize ring-1 ring-inset ${styles[status]}`}
    >
      {status}
    </span>
  );
}

export default function App() {
  const health = useBackendHealth();
  const serviceReady = health.phase === "ready";

  return (
    <div className="min-h-screen bg-slate-50 text-slate-950">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-8 py-5">
          <div>
            <p className="text-sm font-semibold uppercase tracking-[0.18em] text-indigo-700">
              Simplified Billing
            </p>
            <h1 className="mt-1 text-2xl font-bold tracking-tight">
              Desktop application foundation
            </h1>
          </div>
          <div
            className={`flex items-center gap-2 rounded-full px-3 py-2 text-sm font-semibold ${
              serviceReady
                ? "bg-emerald-50 text-emerald-800"
                : health.phase === "loading"
                  ? "bg-amber-50 text-amber-800"
                  : "bg-red-50 text-red-800"
            }`}
            role="status"
            aria-live="polite"
          >
            <span
              className={`h-2.5 w-2.5 rounded-full ${
                serviceReady
                  ? "bg-emerald-500"
                  : health.phase === "loading"
                    ? "bg-amber-500"
                    : "bg-red-500"
              }`}
              aria-hidden="true"
            />
            {serviceReady
              ? "Local service ready"
              : health.phase === "loading"
                ? "Checking local service"
                : "Local service unavailable"}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-8 py-10">
        <section className="grid gap-6 lg:grid-cols-[1.5fr_1fr]">
          <div className="rounded-2xl bg-indigo-700 p-8 text-white shadow-sm">
            <p className="text-sm font-semibold uppercase tracking-[0.16em] text-indigo-200">
              Milestone 1
            </p>
            <h2 className="mt-3 max-w-2xl text-4xl font-bold tracking-tight">
              A reliable offline core before business modules.
            </h2>
            <p className="mt-4 max-w-2xl text-base leading-7 text-indigo-100">
              This shell validates the local desktop, API and database boundary. Store setup,
              authentication and inventory are the next implementation milestone.
            </p>
          </div>

          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <div className="flex items-start justify-between gap-4">
              <div>
                <p className="text-sm font-semibold text-slate-500">System readiness</p>
                <h2 className="mt-1 text-xl font-bold">Local runtime</h2>
              </div>
              {health.phase === "error" && (
                <button
                  type="button"
                  onClick={health.refresh}
                  className="rounded-lg bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
                >
                  Retry
                </button>
              )}
            </div>

            {health.phase === "loading" && (
              <p className="mt-6 text-sm text-slate-600">Connecting to the local backend…</p>
            )}

            {health.phase === "error" && (
              <div className="mt-6 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-900">
                <p className="font-semibold">Backend connection failed</p>
                <p className="mt-1">{health.message}</p>
                <p className="mt-2 text-red-800">
                  Start MySQL and the Spring Boot backend, then retry.
                </p>
              </div>
            )}

            {health.phase === "ready" && (
              <dl className="mt-6 space-y-3 text-sm">
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-3">
                  <dt className="text-slate-500">Backend</dt>
                  <dd className="font-semibold">{health.data.status}</dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-3">
                  <dt className="text-slate-500">Database</dt>
                  <dd className="font-semibold">{health.data.database}</dd>
                </div>
                <div className="flex justify-between gap-4 border-b border-slate-100 pb-3">
                  <dt className="text-slate-500">Application</dt>
                  <dd className="font-semibold">{health.data.version}</dd>
                </div>
                <div className="flex justify-between gap-4">
                  <dt className="text-slate-500">Java</dt>
                  <dd className="font-semibold">{health.data.javaVersion}</dd>
                </div>
              </dl>
            )}
          </div>
        </section>

        <section className="mt-8">
          <div className="flex items-end justify-between gap-6">
            <div>
              <p className="text-sm font-semibold uppercase tracking-[0.14em] text-indigo-700">
                Delivery sequence
              </p>
              <h2 className="mt-1 text-2xl font-bold tracking-tight">Technical milestones</h2>
            </div>
            <p className="max-w-xl text-right text-sm text-slate-600">
              Features are added in transactions-safe modules rather than as one large code drop.
            </p>
          </div>

          <div className="mt-5 grid gap-4 md:grid-cols-2">
            {milestones.map((milestone, index) => (
              <article
                key={milestone.name}
                className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex min-w-0 items-start gap-4">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-900 text-sm font-bold text-white">
                      {index + 1}
                    </span>
                    <div>
                      <h3 className="font-bold">{milestone.name}</h3>
                      <p className="mt-1 text-sm leading-6 text-slate-600">
                        {milestone.description}
                      </p>
                    </div>
                  </div>
                  <StatusBadge status={milestone.status} />
                </div>
              </article>
            ))}
          </div>
        </section>
      </main>
    </div>
  );
}
