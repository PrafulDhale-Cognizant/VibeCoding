import { useEffect, useState } from "react";
import { api } from "../lib/api";

export const STORE_LOGO_UPDATED_EVENT = "billing:store-logo-updated";

export function useStoreLogo(accessToken: string) {
  const [logoUrl, setLogoUrl] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    let currentUrl: string | null = null;

    async function loadLogo() {
      try {
        const logo = await api.getStoreLogo(accessToken);
        if (!active) return;
        const nextUrl = logo ? URL.createObjectURL(logo) : null;
        if (currentUrl) URL.revokeObjectURL(currentUrl);
        currentUrl = nextUrl;
        setLogoUrl(nextUrl);
      } catch {
        if (active) setLogoUrl(null);
      }
    }

    const refresh = () => void loadLogo();
    void loadLogo();
    window.addEventListener(STORE_LOGO_UPDATED_EVENT, refresh);
    return () => {
      active = false;
      window.removeEventListener(STORE_LOGO_UPDATED_EVENT, refresh);
      if (currentUrl) URL.revokeObjectURL(currentUrl);
    };
  }, [accessToken]);

  return logoUrl;
}
