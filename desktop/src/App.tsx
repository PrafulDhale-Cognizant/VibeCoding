import { useEffect, useRef, useState } from "react";
import { LoginScreen } from "./components/LoginScreen";
import { SetupScreen } from "./components/SetupScreen";
import { Workspace } from "./components/Workspace";
import { api } from "./lib/api";
import { clearRefreshToken, loadRefreshToken, storeRefreshToken } from "./lib/session";
import type { AuthResponse, InitialSetupRequest } from "./types";
import { ThemeSettings } from "./components/ThemeSettings";

type AppPhase = "booting" | "recovery" | "setup" | "login" | "authenticated" | "unavailable";
type StartupRecovery = Awaited<ReturnType<NonNullable<Window["billingDesktop"]>["getStartupRecovery"]>>;

export default function App() {
  const [phase, setPhase] = useState<AppPhase>("booting");
  const [shopName, setShopName] = useState<string | null>(null);
  const [session, setSession] = useState<AuthResponse | null>(null);
  const [startupError, setStartupError] = useState("");
  const [recovery, setRecovery] = useState<StartupRecovery>(null);
  const [recoveryPassword, setRecoveryPassword] = useState("");
  const [recoveryBusy, setRecoveryBusy] = useState(false);
  const bootStarted = useRef(false);

  async function acceptSession(nextSession: AuthResponse) {
    await storeRefreshToken(nextSession.refreshToken);
    setSession(nextSession);
    setPhase("authenticated");
  }

  async function bootstrap() {
      try {
        const status = await api.setupStatus();
        setShopName(status.shopName);
        if (!status.configured) {
          setPhase("setup");
          return;
        }

        const savedToken = await loadRefreshToken();
        if (!savedToken) {
          setPhase("login");
          return;
        }

        try {
          await acceptSession(await api.refresh(savedToken));
        } catch {
          await clearRefreshToken();
          setPhase("login");
        }
      } catch (caught) {
        setStartupError(
          caught instanceof Error
            ? caught.message
            : "The local billing service could not be reached."
        );
        setPhase("unavailable");
      }
  }

  useEffect(() => {
    if (bootStarted.current) return;
    bootStarted.current = true;

    async function inspectRecovery() {
      try {
        const available = await window.billingDesktop?.getStartupRecovery();
        if (available) { setRecovery(available); setPhase("recovery"); return; }
      } catch { /* Recovery discovery must not prevent normal startup. */ }
      await bootstrap();
    }
    void inspectRecovery();
  }, []);

  async function restoreLatestAtStartup() {
    if (!window.billingDesktop || recoveryPassword.length < 12) return;
    setRecoveryBusy(true); setStartupError("");
    try {
      const result = await window.billingDesktop.restoreLatestBackup(recoveryPassword);
      Object.entries(result.configuration).forEach(([key, value]) => localStorage.setItem(key, value));
      window.location.reload();
    } catch (caught) {
      setStartupError(caught instanceof Error ? caught.message : "The latest backup could not be restored.");
      setRecoveryBusy(false);
    }
  }

  useEffect(() => {
    if (!session) return;
    const refreshAt = Math.max(
      10_000,
      new Date(session.accessTokenExpiresAt).getTime() - Date.now() - 60_000
    );
    const timer = window.setTimeout(() => {
      api
        .refresh(session.refreshToken)
        .then(acceptSession)
        .catch(async () => {
          await clearRefreshToken();
          setSession(null);
          setPhase("login");
        });
    }, refreshAt);
    return () => window.clearTimeout(timer);
  }, [session]);

  async function completeSetup(request: InitialSetupRequest) {
    const authenticated = await api.initialize(request);
    setShopName(request.store.shopName);
    await acceptSession(authenticated);
  }

  async function login(username: string, password: string) {
    await acceptSession(await api.login(username, password));
  }

  async function logout() {
    const token = session?.refreshToken;
    try {
      if (token) await api.logout(token);
    } finally {
      await clearRefreshToken();
      setSession(null);
      setPhase("login");
    }
  }

  async function passwordChanged() {
    await clearRefreshToken();
    setSession(null);
    setPhase("login");
  }

  if (phase === "booting") {
    return (
      <div className="md-app flex min-h-screen items-center justify-center">
        <div className="md-floating-theme"><ThemeSettings compact /></div>
        <div className="text-center">
          <div className="md-progress mx-auto" />
          <p className="mt-5 text-sm font-semibold text-slate-500">Opening local workspace…</p>
        </div>
      </div>
    );
  }

  if (phase === "recovery" && recovery) {
    return <div className="md-app flex min-h-screen items-center justify-center p-8">
      <div className="md-floating-theme"><ThemeSettings compact /></div>
      <section className="md-card w-full max-w-xl p-8">
        <p className="text-sm font-bold uppercase tracking-wider text-indigo-700">Recovery point available</p>
        <h1 className="mt-2 text-2xl font-bold">Restore before opening the system?</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">Latest valid backup: <strong>{recovery.fileName}</strong><br />Created {new Date(recovery.createdAt).toLocaleString("en-IN")} · {(recovery.size / 1024 / 1024).toFixed(1)} MB</p>
        {startupError && <p className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-800">{startupError}</p>}
        {recovery.restoreAvailable && <label className="mt-5 block text-sm font-bold">Backup password<input type="password" minLength={12} value={recoveryPassword} onChange={(event) => setRecoveryPassword(event.target.value)} className="md-input" /></label>}
        <div className="mt-6 flex gap-3">
          {recovery.restoreAvailable && <button type="button" disabled={recoveryBusy || recoveryPassword.length < 12} onClick={() => void restoreLatestAtStartup()} className="md-button-filled disabled:opacity-40">{recoveryBusy ? "Restoring…" : "Restore latest backup"}</button>}
          <button type="button" disabled={recoveryBusy} onClick={() => { setPhase("booting"); void bootstrap(); }} className="rounded-xl border border-slate-300 px-5 py-3 font-bold">Continue normally</button>
        </div>
        {!recovery.restoreAvailable && <p className="mt-4 text-xs text-slate-500">Restore is enabled in the installed desktop build where the application manages the backend.</p>}
      </section>
    </div>;
  }

  if (phase === "unavailable") {
    return (
      <div className="md-app flex min-h-screen items-center justify-center p-8">
        <div className="md-floating-theme"><ThemeSettings compact /></div>
        <section className="md-card max-w-lg p-8">
          <p className="text-sm font-bold uppercase tracking-wider text-red-700">Service unavailable</p>
          <h1 className="mt-2 text-2xl font-bold">The local backend is not ready</h1>
          <p className="mt-3 text-sm leading-6 text-slate-600">{startupError}</p>
          <p className="mt-3 text-sm text-slate-600">Start MySQL and Spring Boot, then reopen the application.</p>
          <button type="button" onClick={() => window.location.reload()} className="md-button-filled mt-6">
            Retry
          </button>
        </section>
      </div>
    );
  }

  if (phase === "setup") {
    return <SetupScreen onSubmit={completeSetup} />;
  }

  if (phase === "login") {
    return <LoginScreen shopName={shopName} onLogin={login} />;
  }

  if (!session) {
    return null;
  }

  return (
    <Workspace
      session={session}
      onLogout={logout}
      onPasswordChanged={passwordChanged}
    />
  );
}
