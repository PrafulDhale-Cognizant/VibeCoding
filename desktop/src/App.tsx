import { useEffect, useRef, useState } from "react";
import { LoginScreen } from "./components/LoginScreen";
import { SetupScreen } from "./components/SetupScreen";
import { Workspace } from "./components/Workspace";
import { api } from "./lib/api";
import { clearRefreshToken, loadRefreshToken, storeRefreshToken } from "./lib/session";
import type { AuthResponse, InitialSetupRequest } from "./types";

type AppPhase = "booting" | "setup" | "login" | "authenticated" | "unavailable";

export default function App() {
  const [phase, setPhase] = useState<AppPhase>("booting");
  const [shopName, setShopName] = useState<string | null>(null);
  const [session, setSession] = useState<AuthResponse | null>(null);
  const [startupError, setStartupError] = useState("");
  const bootStarted = useRef(false);

  async function acceptSession(nextSession: AuthResponse) {
    await storeRefreshToken(nextSession.refreshToken);
    setSession(nextSession);
    setPhase("authenticated");
  }

  useEffect(() => {
    if (bootStarted.current) return;
    bootStarted.current = true;

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

    void bootstrap();
  }, []);

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
      <div className="flex min-h-screen items-center justify-center bg-slate-950 text-white">
        <div className="text-center">
          <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-indigo-300 border-t-white" />
          <p className="mt-5 text-sm font-semibold text-slate-300">Opening local workspace…</p>
        </div>
      </div>
    );
  }

  if (phase === "unavailable") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-100 p-8">
        <section className="max-w-lg rounded-2xl border border-red-200 bg-white p-8 shadow-sm">
          <p className="text-sm font-bold uppercase tracking-wider text-red-700">Service unavailable</p>
          <h1 className="mt-2 text-2xl font-bold">The local backend is not ready</h1>
          <p className="mt-3 text-sm leading-6 text-slate-600">{startupError}</p>
          <p className="mt-3 text-sm text-slate-600">Start MySQL and Spring Boot, then reopen the application.</p>
          <button type="button" onClick={() => window.location.reload()} className="mt-6 rounded-lg bg-indigo-700 px-4 py-2.5 text-sm font-bold text-white hover:bg-indigo-800">
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
