import { useState, type FormEvent } from "react";
import type { InitialSetupRequest, StoreProfile } from "../types";
import { ErrorNotice, Field, SelectInput, TextInput } from "./FormControls";
import { ThemeSettings } from "./ThemeSettings";

const initialStore: StoreProfile = {
  shopName: "",
  ownerName: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  stateName: "",
  stateCode: "",
  postalCode: "",
  phone: "",
  email: "",
  gstRegistered: false,
  gstin: "",
  currencyCode: "INR",
  timezone: "Asia/Kolkata",
  invoicePrefix: "INV",
  financialYearStartMonth: 4,
  receiptWidth: "MM_80",
  invoicePrintFormat: "THERMAL",
  a4InvoiceTemplate: "MODERN",
  thermalReceiptTemplate: "CLASSIC"
};

export function SetupScreen({
  onSubmit
}: {
  onSubmit: (request: InitialSetupRequest) => Promise<void>;
}) {
  const [store, setStore] = useState(initialStore);
  const [owner, setOwner] = useState({
    username: "admin",
    displayName: "",
    password: "",
    confirmPassword: ""
  });
  const [accepted, setAccepted] = useState(false);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const updateStore = <K extends keyof StoreProfile>(key: K, value: StoreProfile[K]) => {
    setStore((current) => ({ ...current, [key]: value }));
  };

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (owner.password !== owner.confirmPassword) {
      setError("The administrator passwords do not match.");
      return;
    }
    setSubmitting(true);
    try {
      await onSubmit({
        store,
        owner: {
          username: owner.username,
          displayName: owner.displayName,
          password: owner.password
        },
        dataResponsibilityAccepted: accepted
      });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Setup could not be completed.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="md-app min-h-screen px-8 py-10">
      <div className="mx-auto max-w-5xl">
        <div className="mb-8 flex items-center justify-between">
          <div>
            <p className="text-sm font-bold uppercase tracking-[0.18em] text-indigo-700">
              Simplified Billing
            </p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight">Set up this shop</h1>
            <p className="mt-2 text-slate-600">
              This one-time setup creates the local shop profile and first owner account.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className="rounded-full bg-indigo-100 px-4 py-2 text-sm font-semibold text-indigo-800">Step 1 of 1</span>
            <ThemeSettings compact />
          </div>
        </div>

        <form onSubmit={submit} className="space-y-6">
          <section className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
            <div className="border-b border-slate-100 pb-4">
              <h2 className="text-xl font-bold">Shop identity</h2>
              <p className="mt-1 text-sm text-slate-500">
                These details appear on invoices and thermal receipts.
              </p>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-5">
              <Field label="Shop name">
                <TextInput
                  required
                  maxLength={150}
                  autoFocus
                  value={store.shopName}
                  onChange={(event) => updateStore("shopName", event.target.value)}
                />
              </Field>
              <Field label="Owner / proprietor name">
                <TextInput
                  required
                  maxLength={120}
                  value={store.ownerName}
                  onChange={(event) => {
                    updateStore("ownerName", event.target.value);
                    if (!owner.displayName) {
                      setOwner((current) => ({ ...current, displayName: event.target.value }));
                    }
                  }}
                />
              </Field>
              <Field label="Phone">
                <TextInput
                  required
                  inputMode="tel"
                  value={store.phone}
                  onChange={(event) => updateStore("phone", event.target.value)}
                />
              </Field>
              <Field label="Email (optional)">
                <TextInput
                  type="email"
                  maxLength={254}
                  value={store.email}
                  onChange={(event) => updateStore("email", event.target.value)}
                />
              </Field>
              <Field label="Address line 1">
                <TextInput
                  required
                  maxLength={200}
                  value={store.addressLine1}
                  onChange={(event) => updateStore("addressLine1", event.target.value)}
                />
              </Field>
              <Field label="Address line 2 (optional)">
                <TextInput
                  maxLength={200}
                  value={store.addressLine2}
                  onChange={(event) => updateStore("addressLine2", event.target.value)}
                />
              </Field>
              <Field label="City">
                <TextInput
                  required
                  maxLength={100}
                  value={store.city}
                  onChange={(event) => updateStore("city", event.target.value)}
                />
              </Field>
              <Field label="State">
                <TextInput
                  required
                  maxLength={100}
                  value={store.stateName}
                  onChange={(event) => updateStore("stateName", event.target.value)}
                />
              </Field>
              <Field label="GST state code" hint="Two digits, for example 27 for Maharashtra.">
                <TextInput
                  required
                  inputMode="numeric"
                  maxLength={2}
                  value={store.stateCode}
                  onChange={(event) => updateStore("stateCode", event.target.value)}
                />
              </Field>
              <Field label="PIN code">
                <TextInput
                  required
                  inputMode="numeric"
                  maxLength={6}
                  value={store.postalCode}
                  onChange={(event) => updateStore("postalCode", event.target.value)}
                />
              </Field>
            </div>

            <div className="mt-5 rounded-xl bg-slate-50 p-4">
              <label className="flex items-center gap-3 text-sm font-semibold text-slate-800">
                <input
                  type="checkbox"
                  checked={store.gstRegistered}
                  onChange={(event) => updateStore("gstRegistered", event.target.checked)}
                  className="h-4 w-4 rounded border-slate-300 text-indigo-600"
                />
                This shop is GST registered
              </label>
              {store.gstRegistered && (
                <div className="mt-4 max-w-md">
                  <Field label="GSTIN">
                    <TextInput
                      required
                      maxLength={15}
                      value={store.gstin}
                      onChange={(event) => updateStore("gstin", event.target.value.toUpperCase())}
                    />
                  </Field>
                </div>
              )}
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
            <div className="border-b border-slate-100 pb-4">
              <h2 className="text-xl font-bold">Billing defaults</h2>
            </div>
            <div className="mt-5 grid grid-cols-3 gap-5">
              <Field label="Invoice prefix">
                <TextInput
                  required
                  maxLength={12}
                  value={store.invoicePrefix}
                  onChange={(event) => updateStore("invoicePrefix", event.target.value.toUpperCase())}
                />
              </Field>
              <Field label="Financial year starts">
                <SelectInput
                  value={store.financialYearStartMonth}
                  onChange={(event) =>
                    updateStore("financialYearStartMonth", Number(event.target.value))
                  }
                >
                  {Array.from({ length: 12 }, (_, index) => (
                    <option key={index + 1} value={index + 1}>
                      {new Date(2026, index).toLocaleString("en-IN", { month: "long" })}
                    </option>
                  ))}
                </SelectInput>
              </Field>
              <Field label="Thermal receipt width">
                <SelectInput
                  value={store.receiptWidth}
                  onChange={(event) =>
                    updateStore("receiptWidth", event.target.value as StoreProfile["receiptWidth"])
                  }
                >
                  <option value="MM_80">80 mm</option>
                  <option value="MM_58">58 mm</option>
                </SelectInput>
              </Field>
            </div>
          </section>

          <section className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
            <div className="border-b border-slate-100 pb-4">
              <h2 className="text-xl font-bold">Owner account</h2>
              <p className="mt-1 text-sm text-slate-500">
                The owner can manage shop settings, users, and all future modules.
              </p>
            </div>
            <div className="mt-5 grid grid-cols-2 gap-5">
              <Field label="Display name">
                <TextInput
                  required
                  maxLength={120}
                  value={owner.displayName}
                  onChange={(event) =>
                    setOwner((current) => ({ ...current, displayName: event.target.value }))
                  }
                />
              </Field>
              <Field label="Username">
                <TextInput
                  required
                  minLength={3}
                  maxLength={60}
                  autoComplete="username"
                  value={owner.username}
                  onChange={(event) =>
                    setOwner((current) => ({ ...current, username: event.target.value }))
                  }
                />
              </Field>
              <Field
                label="Password"
                hint="At least 12 characters with uppercase, lowercase, number, and special character."
              >
                <TextInput
                  required
                  type="password"
                  minLength={12}
                  maxLength={72}
                  autoComplete="new-password"
                  value={owner.password}
                  onChange={(event) =>
                    setOwner((current) => ({ ...current, password: event.target.value }))
                  }
                />
              </Field>
              <Field label="Confirm password">
                <TextInput
                  required
                  type="password"
                  minLength={12}
                  maxLength={72}
                  autoComplete="new-password"
                  value={owner.confirmPassword}
                  onChange={(event) =>
                    setOwner((current) => ({ ...current, confirmPassword: event.target.value }))
                  }
                />
              </Field>
            </div>
          </section>

          <section className="rounded-xl border border-indigo-200 bg-indigo-50 p-5">
            <label className="flex items-start gap-3 text-sm text-indigo-950">
              <input
                required
                type="checkbox"
                checked={accepted}
                onChange={(event) => setAccepted(event.target.checked)}
                className="mt-0.5 h-4 w-4 rounded border-indigo-300 text-indigo-600"
              />
              <span>
                <strong className="block">I understand this is an offline local database.</strong>
                I am responsible for keeping the computer secure and creating regular backups.
              </span>
            </label>
          </section>

          {error && <ErrorNotice message={error} />}

          <div className="flex justify-end pb-10">
            <button
              disabled={submitting}
              className="md-button-filled px-7 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {submitting ? "Creating secure workspace…" : "Complete setup"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
