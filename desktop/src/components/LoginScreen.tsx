import { useState, type FormEvent } from "react";
import { ErrorNotice, Field, TextInput } from "./FormControls";

export function LoginScreen({
  shopName,
  onLogin
}: {
  shopName: string | null;
  onLogin: (username: string, password: string) => Promise<void>;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      await onLogin(username, password);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Sign in failed.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="grid min-h-screen grid-cols-[1.15fr_0.85fr] bg-slate-950">
      <section className="flex flex-col justify-between bg-indigo-700 p-12 text-white">
        <div>
          <p className="text-sm font-bold uppercase tracking-[0.2em] text-indigo-200">
            Simplified Billing
          </p>
          <h1 className="mt-6 max-w-xl text-5xl font-bold leading-tight tracking-tight">
            Fast billing, dependable stock, fully local.
          </h1>
          <p className="mt-5 max-w-lg text-lg leading-8 text-indigo-100">
            Your shop data stays on this computer and remains available without an internet
            connection.
          </p>
        </div>
        <p className="text-sm text-indigo-200">Local service secured with encrypted sessions</p>
      </section>

      <section className="flex items-center justify-center bg-slate-50 p-12">
        <form
          onSubmit={submit}
          className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 shadow-xl shadow-slate-950/5"
        >
          <p className="text-sm font-semibold text-indigo-700">Welcome back</p>
          <h2 className="mt-2 text-3xl font-bold tracking-tight">{shopName ?? "Your shop"}</h2>
          <p className="mt-2 text-sm text-slate-500">Sign in to open the billing workspace.</p>

          <div className="mt-8 space-y-5">
            <Field label="Username">
              <TextInput
                required
                autoFocus
                autoComplete="username"
                value={username}
                onChange={(event) => setUsername(event.target.value)}
              />
            </Field>
            <Field label="Password">
              <TextInput
                required
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
              />
            </Field>
            {error && <ErrorNotice message={error} />}
            <button
              disabled={submitting}
              className="w-full rounded-xl bg-indigo-700 px-5 py-3 text-sm font-bold text-white hover:bg-indigo-800 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-60"
            >
              {submitting ? "Signing in…" : "Sign in"}
            </button>
          </div>
        </form>
      </section>
    </div>
  );
}
