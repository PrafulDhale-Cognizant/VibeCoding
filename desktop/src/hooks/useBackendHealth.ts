import { useCallback, useEffect, useState } from "react";
import { fetchSystemHealth, type SystemHealth } from "../lib/api";

type HealthState =
  | { phase: "loading"; data?: undefined; message?: undefined }
  | { phase: "ready"; data: SystemHealth; message?: undefined }
  | { phase: "error"; data?: undefined; message: string };

export function useBackendHealth() {
  const [state, setState] = useState<HealthState>({ phase: "loading" });

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setState({ phase: "loading" });
    try {
      const data = await fetchSystemHealth(signal);
      setState({ phase: "ready", data });
    } catch (error) {
      if (signal?.aborted) {
        return;
      }

      const message = error instanceof Error
        ? error.message
        : "The local billing service is unavailable.";
      setState({ phase: "error", message });
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  return {
    ...state,
    refresh: () => void refresh()
  };
}

