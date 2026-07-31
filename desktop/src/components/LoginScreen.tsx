import { useState, type FormEvent } from "react";
import { ErrorNotice, Field, TextInput } from "./FormControls";
import { ThemeSettings } from "./ThemeSettings";
import { AppIcon } from "./AppIcon";

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
    <div className="md-auth-layout">
      <div className="md-floating-theme"><ThemeSettings compact /></div>
      <section className="md-auth-hero">
        <div>
          <div className="md-auth-brand"><span>SB</span><strong>Simplified Billing</strong></div>
          <p className="md-overline mt-16">Retail workspace</p>
          <h1>
            Fast at the counter.<br />Calm everywhere else.
          </h1>
          <p className="md-auth-copy">
            Your shop data stays on this computer and remains available without an internet
            connection.
          </p>
          <div className="md-auth-features">
            <span><AppIcon name="pos" />Scanner-first billing</span>
            <span><AppIcon name="inventory" />Live stock control</span>
            <span><AppIcon name="reports" />Clear business insights</span>
          </div>
        </div>
        <p className="md-auth-footnote"><i /> Local service · Encrypted session · Works offline</p>
      </section>

      <section className="md-auth-form-side">
        <form
          onSubmit={submit}
          className="md-auth-card"
        >
          <p className="md-overline">Welcome back</p>
          <h2>{shopName ?? "Your shop"}</h2>
          <p className="md-auth-subtitle">Sign in to open your local billing workspace.</p>

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
              className="md-button-filled mt-2 w-full disabled:opacity-60"
            >
              {submitting ? "Signing in…" : "Sign in"}
            </button>
          </div>
          <p className="md-auth-help">Use the local account created for this shop.</p>
        </form>
      </section>
    </div>
  );
}
