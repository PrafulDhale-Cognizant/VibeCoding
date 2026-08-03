import { useEffect, useState, type FormEvent } from "react";
import { api } from "../lib/api";
import type {
  AuthResponse,
  StoreDetails,
  StoreProfile,
  UserRole,
  UserSummary
} from "../types";
import {
  ErrorNotice,
  Field,
  SelectInput,
  SuccessNotice,
  TextInput
} from "./FormControls";
import { InventoryPanel } from "./inventory/InventoryPanel";
import { PosPanel } from "./pos/PosPanel";
import { SalesReturnsPanel } from "./pos/SalesReturnsPanel";
import { KhataPanel } from "./khata/KhataPanel";
import { ReportsPanel } from "./reports/ReportsPanel";
import { PurchasingPanel } from "./purchasing/PurchasingPanel";
import { AppIcon, type AppIconName } from "./AppIcon";
import { ThemeSettings } from "./ThemeSettings";
import { OperationsPanel } from "./OperationsPanel";
import { STORE_LOGO_UPDATED_EVENT, useStoreLogo } from "../hooks/useStoreLogo";

type Section = "home" | "pos" | "returns" | "khata" | "inventory" | "purchases" | "reports" | "operations" | "store" | "users" | "account";

const roleLabels: Record<UserRole, string> = {
  OWNER: "Owner",
  ADMIN: "Administrator",
  CASHIER: "Cashier",
  INVENTORY_MANAGER: "Inventory manager",
  VIEWER: "Viewer"
};

const allRoles: UserRole[] = ["OWNER", "ADMIN", "CASHIER", "INVENTORY_MANAGER", "VIEWER"];

function messageFrom(caught: unknown, fallback: string) {
  return caught instanceof Error ? caught.message : fallback;
}

export function Workspace({
  session,
  onLogout,
  onPasswordChanged
}: {
  session: AuthResponse;
  onLogout: () => Promise<void>;
  onPasswordChanged: () => Promise<void>;
}) {
  const [section, setSection] = useState<Section>("home");
  const [returnInvoiceNumber, setReturnInvoiceNumber] = useState("");
  const logoUrl = useStoreLogo(session.accessToken);

  useEffect(() => {
    const configured = Number(localStorage.getItem("simplified-billing.security.inactivity-minutes") ?? "15");
    if (!Number.isFinite(configured) || configured <= 0) return;
    let timer = window.setTimeout(() => void onLogout(), configured * 60_000);
    const reset = () => {
      window.clearTimeout(timer);
      timer = window.setTimeout(() => void onLogout(), configured * 60_000);
    };
    const events: Array<keyof WindowEventMap> = ["pointerdown", "keydown", "touchstart"];
    events.forEach((name) => window.addEventListener(name, reset, { passive: true }));
    return () => {
      window.clearTimeout(timer);
      events.forEach((name) => window.removeEventListener(name, reset));
    };
  }, [onLogout]);
  const canAdminister = session.user.roles.some((role) => role === "OWNER" || role === "ADMIN");
  const canReadInventory = session.user.roles.some((role) =>
    role === "OWNER" || role === "ADMIN" || role === "INVENTORY_MANAGER" || role === "VIEWER"
  );
  const canWriteInventory = session.user.roles.some((role) =>
    role === "OWNER" || role === "ADMIN" || role === "INVENTORY_MANAGER"
  );
  const canUsePos = session.user.roles.some((role) =>
    role === "OWNER" || role === "ADMIN" || role === "CASHIER"
  );
  const canViewReports = session.user.roles.some((role) =>
    role === "OWNER" || role === "ADMIN" || role === "VIEWER"
  );
  const canUsePurchasing = session.user.roles.some((role) =>
    role === "OWNER" || role === "ADMIN" || role === "INVENTORY_MANAGER"
  );

  const navigation: Array<{ id: Section; label: string; visible: boolean; icon: AppIconName }> = [
    { id: "home", label: "Home", visible: true, icon: "home" },
    { id: "pos", label: "Point of sale", visible: canUsePos, icon: "pos" },
    { id: "returns", label: "Sales returns", visible: canAdminister, icon: "pos" },
    { id: "khata", label: "Khata", visible: canUsePos, icon: "khata" },
    { id: "inventory", label: "Inventory", visible: canReadInventory, icon: "inventory" },
    { id: "purchases", label: "Purchases & suppliers", visible: canUsePurchasing, icon: "purchases" },
    { id: "reports", label: "Reports & invoices", visible: canViewReports, icon: "reports" },
    { id: "operations", label: "Backup & diagnostics", visible: canAdminister, icon: "system" },
    { id: "store", label: "Shop settings", visible: true, icon: "store" },
    { id: "users", label: "Users & roles", visible: canAdminister, icon: "users" },
    { id: "account", label: "My account", visible: true, icon: "account" }
  ];

  const activeItem = navigation.find((item) => item.id === section);
  const initials = session.user.displayName
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join("");

  return (
    <div className="md-app flex min-h-screen">
      <aside className="md-drawer">
        <div className="md-brand">
          <div className={`md-brand-mark ${logoUrl ? "md-brand-logo" : ""}`}>
            {logoUrl ? <img src={logoUrl} alt="Shop logo" /> : "SB"}
          </div>
          <div className="min-w-0">
            <strong>Simplified Billing</strong>
            <span>Offline retail workspace</span>
          </div>
        </div>

        <nav className="md-nav" aria-label="Main navigation">
          <p className="md-nav-label">Workspace</p>
          {navigation
            .filter((item) => item.visible)
            .map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setSection(item.id)}
                className={`md-nav-item ${section === item.id ? "selected" : ""}`}
                aria-current={section === item.id ? "page" : undefined}
              >
                <AppIcon name={item.icon} />
                {item.label}
              </button>
            ))}
        </nav>

        <div className="md-drawer-footer">
          <ThemeSettings />
          <div className="md-user-card">
            <span className="md-avatar">{initials || "U"}</span>
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-bold">{session.user.displayName}</p>
              <p className="mt-0.5 truncate text-[10px] text-slate-500">
                {roleLabels[session.user.roles[0]] ?? `@${session.user.username}`}
              </p>
            </div>
            <button
              type="button"
              onClick={() => void onLogout()}
              className="md-icon-button"
              title="Sign out"
              aria-label="Sign out"
            >
              <AppIcon name="logout" className="h-5 w-5" />
            </button>
          </div>
        </div>
      </aside>

      <div className="min-w-0 flex-1">
        <header className="md-topbar">
          <div>
            <p>
                {section === "pos"
                  ? "Scanner-first checkout"
                  : section === "returns"
                    ? "Cancellation, returns & refunds"
                  : section === "khata"
                    ? "Customer credit & settlements"
                  : section === "inventory"
                    ? "Catalog & stock control"
                  : section === "purchases"
                    ? "Stock receiving & supplier payables"
                    : section === "reports"
                      ? "Sales insights, invoice lookup & reprints"
                    : section === "operations"
                      ? "Backup, recovery & local health"
                    : "Store setup & authentication"}
            </p>
            <h2>{activeItem?.label}</h2>
          </div>
          <div className="flex items-center gap-2">
            <span className="md-status-chip">Offline service ready</span>
            <ThemeSettings compact />
          </div>
        </header>

        <main className={`md-content ${section === "pos" ? "p-5" : "p-8"}`}>
          {section === "home" && <HomePanel accessToken={session.accessToken} user={session.user} />}
          {section === "pos" && canUsePos && <PosPanel accessToken={session.accessToken} />}
          {section === "returns" && canAdminister && <SalesReturnsPanel accessToken={session.accessToken} initialInvoiceNumber={returnInvoiceNumber} />}
          {section === "khata" && canUsePos && <KhataPanel accessToken={session.accessToken} />}
          {section === "inventory" && canReadInventory && (
            <InventoryPanel accessToken={session.accessToken} canWrite={canWriteInventory} />
          )}
          {section === "purchases" && canUsePurchasing && (
            <PurchasingPanel accessToken={session.accessToken} canPay={canAdminister} />
          )}
          {section === "reports" && canViewReports && (
            <ReportsPanel
              accessToken={session.accessToken}
              canViewInvoices={canAdminister}
              onStartReturn={(invoiceNumber) => { setReturnInvoiceNumber(invoiceNumber); setSection("returns"); }}
            />
          )}
          {section === "operations" && canAdminister && <OperationsPanel />}
          {section === "store" && (
            <StorePanel accessToken={session.accessToken} canEdit={canAdminister} />
          )}
          {section === "users" && canAdminister && (
            <UsersPanel accessToken={session.accessToken} currentUser={session.user} />
          )}
          {section === "account" && (
            <AccountPanel
              accessToken={session.accessToken}
              user={session.user}
              onPasswordChanged={onPasswordChanged}
            />
          )}
        </main>
      </div>
    </div>
  );
}

function HomePanel({ accessToken, user }: { accessToken: string; user: UserSummary }) {
  const [store, setStore] = useState<StoreDetails | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api.getStore(accessToken).then(setStore).catch((caught) => setError(messageFrom(caught, "Shop could not be loaded.")));
  }, [accessToken]);

  return (
    <div className="mx-auto max-w-6xl">
      {error && <ErrorNotice message={error} />}
      <section className="rounded-2xl bg-indigo-700 p-8 text-white shadow-sm">
        <p className="text-sm font-semibold text-indigo-200">Good to see you, {user.displayName}</p>
        <h3 className="mt-2 text-4xl font-bold tracking-tight">
          {store?.shopName ?? "Loading shop…"}
        </h3>
        <p className="mt-4 max-w-2xl leading-7 text-indigo-100">
          Store setup, authentication, inventory and point-of-sale billing are ready. Scan products,
          collect payment and print thermal receipts from the Point of sale workspace.
        </p>
      </section>

      <section className="mt-6 grid grid-cols-3 gap-5">
        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-semibold text-slate-500">Signed in as</p>
          <p className="mt-2 text-xl font-bold">{user.username}</p>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-semibold text-slate-500">Receipt format</p>
          <p className="mt-2 text-xl font-bold">
            {store ? store.receiptWidth.replace("MM_", "") + " mm" : "—"}
          </p>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-sm font-semibold text-slate-500">GST status</p>
          <p className="mt-2 text-xl font-bold">
            {store ? (store.gstRegistered ? "Registered" : "Not registered") : "—"}
          </p>
        </article>
      </section>
    </div>
  );
}

function toProfile(store: StoreDetails): StoreProfile {
  return {
    shopName: store.shopName,
    ownerName: store.ownerName,
    addressLine1: store.addressLine1,
    addressLine2: store.addressLine2 ?? "",
    city: store.city,
    stateName: store.stateName,
    stateCode: store.stateCode,
    postalCode: store.postalCode,
    phone: store.phone,
    email: store.email ?? "",
    gstRegistered: store.gstRegistered,
    gstin: store.gstin ?? "",
    currencyCode: "INR",
    timezone: "Asia/Kolkata",
    invoicePrefix: store.invoicePrefix,
    financialYearStartMonth: store.financialYearStartMonth,
    receiptWidth: store.receiptWidth
  };
}

function StorePanel({ accessToken, canEdit }: { accessToken: string; canEdit: boolean }) {
  const [details, setDetails] = useState<StoreDetails | null>(null);
  const [profile, setProfile] = useState<StoreProfile | null>(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    api
      .getStore(accessToken)
      .then((store) => {
        setDetails(store);
        setProfile(toProfile(store));
      })
      .catch((caught) => setError(messageFrom(caught, "Shop settings could not be loaded.")));
  }, [accessToken]);

  const update = <K extends keyof StoreProfile>(key: K, value: StoreProfile[K]) => {
    setProfile((current) => (current ? { ...current, [key]: value } : current));
  };

  async function save(event: FormEvent) {
    event.preventDefault();
    if (!details || !profile) return;
    setSaving(true);
    setError("");
    setSuccess("");
    try {
      const updated = await api.updateStore(accessToken, profile, details.version);
      setDetails(updated);
      setProfile(toProfile(updated));
      setSuccess("Shop settings saved.");
    } catch (caught) {
      setError(messageFrom(caught, "Shop settings could not be saved."));
    } finally {
      setSaving(false);
    }
  }

  async function uploadLogo(file: File) {
    setError("");
    try {
      const updated = await api.updateLogo(accessToken, file);
      setDetails(updated);
      window.dispatchEvent(new Event(STORE_LOGO_UPDATED_EVENT));
      setSuccess("Shop logo saved.");
    } catch (caught) {
      setError(messageFrom(caught, "Logo could not be uploaded."));
    }
  }

  async function removeLogo() {
    setError("");
    try {
      await api.deleteLogo(accessToken);
      setDetails((current) => (current ? { ...current, logoAvailable: false } : current));
      window.dispatchEvent(new Event(STORE_LOGO_UPDATED_EVENT));
      setSuccess("Shop logo removed.");
    } catch (caught) {
      setError(messageFrom(caught, "Logo could not be removed."));
    }
  }

  if (!profile || !details) {
    return <p className="text-sm text-slate-600">{error || "Loading shop settings…"}</p>;
  }

  return (
    <form onSubmit={save} className="mx-auto max-w-5xl space-y-6">
      {!canEdit && (
        <div className="rounded-lg bg-amber-50 p-4 text-sm text-amber-900">
          Shop settings are read-only for your role.
        </div>
      )}
      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}

      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-bold">Business details</h3>
        <div className="mt-5 grid grid-cols-2 gap-5">
          <Field label="Shop name">
            <TextInput disabled={!canEdit} required value={profile.shopName} onChange={(event) => update("shopName", event.target.value)} />
          </Field>
          <Field label="Owner name">
            <TextInput disabled={!canEdit} required value={profile.ownerName} onChange={(event) => update("ownerName", event.target.value)} />
          </Field>
          <Field label="Phone">
            <TextInput disabled={!canEdit} required value={profile.phone} onChange={(event) => update("phone", event.target.value)} />
          </Field>
          <Field label="Email">
            <TextInput disabled={!canEdit} type="email" value={profile.email} onChange={(event) => update("email", event.target.value)} />
          </Field>
          <Field label="Address line 1">
            <TextInput disabled={!canEdit} required value={profile.addressLine1} onChange={(event) => update("addressLine1", event.target.value)} />
          </Field>
          <Field label="Address line 2">
            <TextInput disabled={!canEdit} value={profile.addressLine2} onChange={(event) => update("addressLine2", event.target.value)} />
          </Field>
          <Field label="City">
            <TextInput disabled={!canEdit} required value={profile.city} onChange={(event) => update("city", event.target.value)} />
          </Field>
          <Field label="State">
            <TextInput disabled={!canEdit} required value={profile.stateName} onChange={(event) => update("stateName", event.target.value)} />
          </Field>
          <Field label="State code">
            <TextInput disabled={!canEdit} required maxLength={2} value={profile.stateCode} onChange={(event) => update("stateCode", event.target.value)} />
          </Field>
          <Field label="PIN code">
            <TextInput disabled={!canEdit} required maxLength={6} value={profile.postalCode} onChange={(event) => update("postalCode", event.target.value)} />
          </Field>
        </div>
      </section>

      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-bold">Tax & receipt</h3>
        <div className="mt-5 grid grid-cols-3 gap-5">
          <Field label="Invoice prefix">
            <TextInput disabled={!canEdit} required value={profile.invoicePrefix} onChange={(event) => update("invoicePrefix", event.target.value.toUpperCase())} />
          </Field>
          <Field label="Financial year start month">
            <SelectInput disabled={!canEdit} value={profile.financialYearStartMonth} onChange={(event) => update("financialYearStartMonth", Number(event.target.value))}>
              {Array.from({ length: 12 }, (_, index) => <option key={index + 1} value={index + 1}>{new Date(2026, index).toLocaleString("en-IN", { month: "long" })}</option>)}
            </SelectInput>
          </Field>
          <Field label="Receipt width">
            <SelectInput disabled={!canEdit} value={profile.receiptWidth} onChange={(event) => update("receiptWidth", event.target.value as StoreProfile["receiptWidth"])}>
              <option value="MM_80">80 mm</option>
              <option value="MM_58">58 mm</option>
            </SelectInput>
          </Field>
        </div>
        <div className="mt-5 flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm font-semibold">
            <input disabled={!canEdit} type="checkbox" checked={profile.gstRegistered} onChange={(event) => update("gstRegistered", event.target.checked)} />
            GST registered
          </label>
          {profile.gstRegistered && (
            <TextInput disabled={!canEdit} required placeholder="GSTIN" className="mt-0 max-w-xs" value={profile.gstin} onChange={(event) => update("gstin", event.target.value.toUpperCase())} />
          )}
        </div>
      </section>

      {canEdit && (
        <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h3 className="text-lg font-bold">Receipt logo</h3>
          <p className="mt-1 text-sm text-slate-500">PNG or JPEG, maximum 2 MB.</p>
          <div className="mt-4 flex items-center gap-3">
            <label className="cursor-pointer rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-semibold hover:bg-slate-50">
              {details.logoAvailable ? "Replace logo" : "Upload logo"}
              <input
                className="hidden"
                type="file"
                accept="image/png,image/jpeg"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) void uploadLogo(file);
                }}
              />
            </label>
            {details.logoAvailable && (
              <button type="button" onClick={() => void removeLogo()} className="text-sm font-semibold text-red-700 hover:text-red-800">
                Remove logo
              </button>
            )}
          </div>
        </section>
      )}

      {canEdit && (
        <div className="flex justify-end">
          <button disabled={saving} className="rounded-xl bg-indigo-700 px-6 py-3 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-60">
            {saving ? "Saving…" : "Save shop settings"}
          </button>
        </div>
      )}
    </form>
  );
}

function RolePicker({
  value,
  onChange,
  ownerAllowed
}: {
  value: UserRole[];
  onChange: (roles: UserRole[]) => void;
  ownerAllowed: boolean;
}) {
  return (
    <div className="flex flex-wrap gap-3">
      {allRoles.filter((role) => ownerAllowed || role !== "OWNER").map((role) => (
        <label key={role} className="flex items-center gap-2 text-xs font-semibold text-slate-700">
          <input
            type="checkbox"
            checked={value.includes(role)}
            onChange={(event) =>
              onChange(event.target.checked ? [...value, role] : value.filter((item) => item !== role))
            }
          />
          {roleLabels[role]}
        </label>
      ))}
    </div>
  );
}

function UsersPanel({ accessToken, currentUser }: { accessToken: string; currentUser: UserSummary }) {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [form, setForm] = useState({
    username: "",
    displayName: "",
    password: "",
    roles: ["CASHIER"] as UserRole[]
  });
  const ownerAllowed = currentUser.roles.includes("OWNER");

  const load = () =>
    api.listUsers(accessToken).then(setUsers).catch((caught) => setError(messageFrom(caught, "Users could not be loaded.")));

  useEffect(() => {
    void load();
  }, [accessToken]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");
    try {
      await api.createUser(accessToken, form);
      setForm({ username: "", displayName: "", password: "", roles: ["CASHIER"] });
      setSuccess("User created.");
      await load();
    } catch (caught) {
      setError(messageFrom(caught, "User could not be created."));
    }
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      {error && <ErrorNotice message={error} />}
      {success && <SuccessNotice message={success} />}
      <form onSubmit={create} className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h3 className="text-lg font-bold">Add local user</h3>
        <div className="mt-5 grid grid-cols-3 gap-4">
          <Field label="Display name">
            <TextInput required value={form.displayName} onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))} />
          </Field>
          <Field label="Username">
            <TextInput required minLength={3} value={form.username} onChange={(event) => setForm((current) => ({ ...current, username: event.target.value }))} />
          </Field>
          <Field label="Temporary password">
            <TextInput required type="password" minLength={12} value={form.password} onChange={(event) => setForm((current) => ({ ...current, password: event.target.value }))} />
          </Field>
        </div>
        <div className="mt-5 flex items-center justify-between gap-5">
          <RolePicker value={form.roles} ownerAllowed={ownerAllowed} onChange={(roles) => setForm((current) => ({ ...current, roles }))} />
          <button disabled={form.roles.length === 0} className="rounded-lg bg-indigo-700 px-5 py-2.5 text-sm font-bold text-white hover:bg-indigo-800 disabled:opacity-50">
            Create user
          </button>
        </div>
      </form>

      <section className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 px-6 py-4">
          <h3 className="text-lg font-bold">Users</h3>
        </div>
        <div className="divide-y divide-slate-100">
          {users.map((user) => (
            <UserEditor
              key={`${user.id}-${user.version}`}
              accessToken={accessToken}
              user={user}
              currentUser={currentUser}
              ownerAllowed={ownerAllowed}
              onChanged={async (message) => {
                setSuccess(message);
                await load();
              }}
              onError={setError}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function UserEditor({
  accessToken,
  user,
  currentUser,
  ownerAllowed,
  onChanged,
  onError
}: {
  accessToken: string;
  user: UserSummary;
  currentUser: UserSummary;
  ownerAllowed: boolean;
  onChanged: (message: string) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [displayName, setDisplayName] = useState(user.displayName);
  const [roles, setRoles] = useState(user.roles);
  const [active, setActive] = useState(user.active);
  const [newPassword, setNewPassword] = useState("");
  const touchesOwner = user.roles.includes("OWNER");
  const canEdit = ownerAllowed || !touchesOwner;

  async function save() {
    onError("");
    try {
      await api.updateUser(accessToken, user.id, { displayName, roles, active, version: user.version });
      await onChanged("User updated.");
    } catch (caught) {
      onError(messageFrom(caught, "User could not be updated."));
    }
  }

  async function reset() {
    onError("");
    try {
      await api.resetPassword(accessToken, user.id, newPassword);
      setNewPassword("");
      await onChanged("Password reset; existing sessions were revoked.");
    } catch (caught) {
      onError(messageFrom(caught, "Password could not be reset."));
    }
  }

  return (
    <article className="p-6">
      <div className="grid grid-cols-[1fr_1.6fr_auto] items-start gap-5">
        <div>
          <TextInput disabled={!canEdit} className="mt-0" value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
          <p className="mt-2 text-xs text-slate-500">@{user.username}</p>
        </div>
        <RolePicker value={roles} ownerAllowed={ownerAllowed} onChange={setRoles} />
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-2 text-xs font-semibold">
            <input disabled={!canEdit || user.id === currentUser.id} type="checkbox" checked={active} onChange={(event) => setActive(event.target.checked)} />
            Active
          </label>
          <button type="button" disabled={!canEdit || roles.length === 0} onClick={() => void save()} className="rounded-lg border border-slate-300 px-3 py-2 text-xs font-bold hover:bg-slate-50 disabled:opacity-50">
            Save
          </button>
        </div>
      </div>
      {canEdit && (
        <div className="mt-4 flex max-w-xl items-end gap-3">
          <Field label="Reset password">
            <TextInput type="password" minLength={12} placeholder="New strong password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
          </Field>
          <button type="button" disabled={!newPassword} onClick={() => void reset()} className="mb-0.5 shrink-0 rounded-lg border border-slate-300 px-3 py-2.5 text-xs font-bold hover:bg-slate-50 disabled:opacity-50">
            Reset
          </button>
        </div>
      )}
    </article>
  );
}

function AccountPanel({
  accessToken,
  user,
  onPasswordChanged
}: {
  accessToken: string;
  user: UserSummary;
  onPasswordChanged: () => Promise<void>;
}) {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (newPassword !== confirmPassword) {
      setError("The new passwords do not match.");
      return;
    }
    try {
      await api.changePassword(accessToken, currentPassword, newPassword);
      await onPasswordChanged();
    } catch (caught) {
      setError(messageFrom(caught, "Password could not be changed."));
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <section className="rounded-2xl border border-slate-200 bg-white p-7 shadow-sm">
        <h3 className="text-xl font-bold">Account security</h3>
        <dl className="mt-5 grid grid-cols-2 gap-4 rounded-xl bg-slate-50 p-4 text-sm">
          <div><dt className="text-slate-500">Username</dt><dd className="mt-1 font-bold">{user.username}</dd></div>
          <div><dt className="text-slate-500">Roles</dt><dd className="mt-1 font-bold">{user.roles.map((role) => roleLabels[role]).join(", ")}</dd></div>
        </dl>
        <form onSubmit={submit} className="mt-7 space-y-5">
          <Field label="Current password">
            <TextInput required type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} />
          </Field>
          <Field label="New password" hint="12-72 characters with upper/lowercase, number, and special character.">
            <TextInput required type="password" minLength={12} autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
          </Field>
          <Field label="Confirm new password">
            <TextInput required type="password" minLength={12} autoComplete="new-password" value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} />
          </Field>
          {error && <ErrorNotice message={error} />}
          <button className="rounded-xl bg-indigo-700 px-6 py-3 text-sm font-bold text-white hover:bg-indigo-800">
            Change password and sign out
          </button>
        </form>
      </section>
    </div>
  );
}
