import {
  createContext,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useState,
  type ReactNode
} from "react";

export type ThemePalette = "ocean" | "teal" | "rose" | "amber" | "violet";
export type ThemeMode = "light" | "dark" | "system";
export type ResolvedThemeMode = "light" | "dark";

export const themePalettes: Array<{
  id: ThemePalette;
  name: string;
  description: string;
  swatches: [string, string, string];
}> = [
  { id: "ocean", name: "Ocean", description: "Calm and dependable", swatches: ["#4054c7", "#dfe2ff", "#59c5ff"] },
  { id: "teal", name: "Teal", description: "Fresh and focused", swatches: ["#006a63", "#9cf2e8", "#35b8ab"] },
  { id: "rose", name: "Rose", description: "Warm and welcoming", swatches: ["#9c414c", "#ffdadc", "#e67382"] },
  { id: "amber", name: "Amber", description: "Bright and energetic", swatches: ["#835500", "#ffddb0", "#f5a623"] },
  { id: "violet", name: "Violet", description: "Creative and premium", swatches: ["#73558f", "#efdbff", "#aa7bc7"] }
];

interface ThemeContextValue {
  palette: ThemePalette;
  mode: ThemeMode;
  resolvedMode: ResolvedThemeMode;
  setPalette: (palette: ThemePalette) => void;
  setMode: (mode: ThemeMode) => void;
}

const STORAGE_KEY = "simplified-billing.theme.v1";
const ThemeContext = createContext<ThemeContextValue | null>(null);

function loadPreference(): { palette: ThemePalette; mode: ThemeMode } {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "{}") as {
      palette?: ThemePalette;
      mode?: ThemeMode;
    };
    const palette = themePalettes.some((item) => item.id === parsed.palette)
      ? parsed.palette!
      : "ocean";
    const mode = parsed.mode === "light" || parsed.mode === "dark" || parsed.mode === "system"
      ? parsed.mode
      : "system";
    return { palette, mode };
  } catch {
    return { palette: "ocean", mode: "system" };
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const initial = useMemo(loadPreference, []);
  const [palette, setPalette] = useState<ThemePalette>(initial.palette);
  const [mode, setMode] = useState<ThemeMode>(initial.mode);
  const [systemDark, setSystemDark] = useState(
    () => window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? false
  );
  const resolvedMode: ResolvedThemeMode = mode === "system" ? (systemDark ? "dark" : "light") : mode;

  useEffect(() => {
    const media = window.matchMedia("(prefers-color-scheme: dark)");
    const update = (event: MediaQueryListEvent) => setSystemDark(event.matches);
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);

  useLayoutEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = palette;
    root.dataset.mode = resolvedMode;
    root.style.colorScheme = resolvedMode;
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ palette, mode }));
    } catch {
      // The selected theme still applies for this session when storage is unavailable.
    }
  }, [palette, mode, resolvedMode]);

  const value = useMemo<ThemeContextValue>(
    () => ({ palette, mode, resolvedMode, setPalette, setMode }),
    [palette, mode, resolvedMode]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used inside ThemeProvider.");
  return context;
}
