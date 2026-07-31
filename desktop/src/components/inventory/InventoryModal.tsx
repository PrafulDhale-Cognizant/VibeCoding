import { useEffect, type ReactNode } from "react";

export function InventoryModal({
  title,
  description,
  onClose,
  children,
  width = "max-w-3xl"
}: {
  title: string;
  description?: string;
  onClose: () => void;
  children: ReactNode;
  width?: string;
}) {
  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/55 p-6"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        className={`max-h-[92vh] w-full ${width} overflow-hidden rounded-2xl bg-white shadow-2xl`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="inventory-modal-title"
      >
        <header className="flex items-start justify-between border-b border-slate-200 px-6 py-5">
          <div>
            <h3 id="inventory-modal-title" className="text-xl font-bold text-slate-950">
              {title}
            </h3>
            {description && <p className="mt-1 text-sm text-slate-500">{description}</p>}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 px-3 py-1.5 text-sm font-bold text-slate-600 hover:bg-slate-50"
            aria-label="Close dialog"
          >
            Close
          </button>
        </header>
        <div className="max-h-[calc(92vh-85px)] overflow-y-auto p-6">{children}</div>
      </section>
    </div>
  );
}
