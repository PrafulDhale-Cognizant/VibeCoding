import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

export function Field({
  label,
  hint,
  children
}: {
  label: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <label className="md-field">
      <span className="md-field-label">{label}</span>
      {children}
      {hint && <span className="md-field-hint">{hint}</span>}
    </label>
  );
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`md-input ${props.className ?? ""}`}
    />
  );
}

export function SelectInput(props: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={`md-input md-select ${props.className ?? ""}`}
    />
  );
}

export function ErrorNotice({ message }: { message: string }) {
  return (
    <div
      className="md-notice md-notice-error"
      role="alert"
    >
      {message}
    </div>
  );
}

export function SuccessNotice({ message }: { message: string }) {
  return (
    <div
      className="md-notice md-notice-success"
      role="status"
    >
      {message}
    </div>
  );
}
