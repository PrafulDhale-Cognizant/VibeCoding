import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { themePalettes, useTheme, type ThemeMode } from "../theme/ThemeProvider";
import { AppIcon, type AppIconName } from "./AppIcon";

const modes: Array<{ id: ThemeMode; label: string; icon: AppIconName }> = [
  { id: "light", label: "Light", icon: "sun" },
  { id: "dark", label: "Dark", icon: "moon" },
  { id: "system", label: "System", icon: "system" }
];

export function ThemeSettings({ compact = false }: { compact?: boolean }) {
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const closeRef = useRef<HTMLButtonElement>(null);
  const { palette, mode, resolvedMode, setPalette, setMode } = useTheme();

  useEffect(() => {
    if (!open) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    requestAnimationFrame(() => closeRef.current?.focus());
    window.addEventListener("keydown", close);
    return () => {
      document.body.style.overflow = previousOverflow;
      window.removeEventListener("keydown", close);
      triggerRef.current?.focus();
    };
  }, [open]);

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        onClick={() => setOpen(true)}
        className={compact ? "md-icon-button" : "md-theme-trigger"}
        aria-label="Choose appearance and color theme"
        title="Appearance"
      >
        <AppIcon name="palette" className="h-5 w-5" />
        {!compact && <span>Appearance</span>}
      </button>

      {open && createPortal(
        <div className="md-dialog-layer" role="presentation" onMouseDown={() => setOpen(false)}>
          <section
            className="md-dialog md-theme-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="theme-dialog-title"
            onMouseDown={(event) => event.stopPropagation()}
          >
            <div className="md-dialog-header">
              <div>
                <p className="md-overline">Personalize workspace</p>
                <h2 id="theme-dialog-title">Appearance</h2>
                <p>Choose a comfortable mode and one of five color palettes.</p>
              </div>
              <button ref={closeRef} type="button" className="md-icon-button" onClick={() => setOpen(false)} aria-label="Close appearance settings">
                <AppIcon name="close" className="h-5 w-5" />
              </button>
            </div>

            <div className="md-dialog-section">
              <h3>Mode</h3>
              <div className="md-segmented" role="radiogroup" aria-label="Appearance mode">
                {modes.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    role="radio"
                    aria-checked={mode === item.id}
                    className={mode === item.id ? "selected" : ""}
                    onClick={() => setMode(item.id)}
                  >
                    <AppIcon name={item.icon} className="h-5 w-5" />
                    <span>{item.label}</span>
                    {item.id === "system" && <small>({resolvedMode})</small>}
                  </button>
                ))}
              </div>
            </div>

            <div className="md-dialog-section">
              <h3>Color palette</h3>
              <div className="md-palette-grid" role="radiogroup" aria-label="Color palette">
                {themePalettes.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    role="radio"
                    aria-checked={palette === item.id}
                    className={palette === item.id ? "selected" : ""}
                    onClick={() => setPalette(item.id)}
                  >
                    <span className="md-swatches" aria-hidden="true">
                      {item.swatches.map((swatch) => <i key={swatch} style={{ backgroundColor: swatch }} />)}
                    </span>
                    <span className="min-w-0 flex-1 text-left">
                      <strong>{item.name}</strong>
                      <small>{item.description}</small>
                    </span>
                    {palette === item.id && <AppIcon name="check" className="h-5 w-5" />}
                  </button>
                ))}
              </div>
            </div>

            <div className="md-dialog-actions">
              <span>Your preference is saved on this computer.</span>
              <button type="button" className="md-button-filled" onClick={() => setOpen(false)}>Done</button>
            </div>
          </section>
        </div>,
        document.body
      )}
    </>
  );
}
