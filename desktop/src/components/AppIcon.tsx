import type { SVGProps } from "react";

export type AppIconName =
  | "home" | "pos" | "khata" | "inventory" | "reports" | "store" | "users"
  | "account" | "palette" | "sun" | "moon" | "system" | "logout" | "check" | "close";

const paths: Record<AppIconName, string> = {
  home: "M3 10.8 12 3l9 7.8v9.7a.5.5 0 0 1-.5.5H15v-6H9v6H3.5a.5.5 0 0 1-.5-.5v-9.7Z",
  pos: "M4 4h16v4H4V4Zm1 6h14v10H5V10Zm3 3v4h4v-4H8Zm7 0h2v2h-2v-2Z",
  khata: "M5 3h12a2 2 0 0 1 2 2v16H7a2 2 0 0 1-2-2V3Zm3 3v2h8V6H8Zm0 5v2h8v-2H8Zm0 5v2h5v-2H8ZM3 5h2v14H3V5Z",
  inventory: "m4 7 8-4 8 4-8 4-8-4Zm1 3 6 3v8l-6-3v-8Zm8 3 6-3v8l-6 3v-8Z",
  reports: "M4 20V10h4v10H4Zm6 0V4h4v16h-4Zm6 0v-7h4v7h-4Z",
  store: "M4 10v10h16V10M3 4h18l-2 5H5L3 4Zm5 9h3v7H8v-7Zm6 0h3v3h-3v-3Z",
  users: "M8 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm8-1a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM1 21v-3c0-3.3 3.1-5 7-5s7 1.7 7 5v3H1Zm15 0v-3c0-1.5-.5-2.8-1.5-3.8.5-.1 1-.2 1.5-.2 3.4 0 6 1.5 6 4.5V21h-6Z",
  account: "M12 12a5 5 0 1 0 0-10 5 5 0 0 0 0 10ZM3 22v-2c0-4 4-6 9-6s9 2 9 6v2H3Z",
  palette: "M12 3a9 9 0 1 0 0 18h1.5a1.5 1.5 0 0 0 0-3H12a2 2 0 0 1 0-4h4a5 5 0 0 0 5-5c0-3.3-4-6-9-6ZM7 9a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3Zm4-2a1.5 1.5 0 1 1 3 0 1.5 1.5 0 0 1-3 0Zm5 4a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3ZM6 14a1.5 1.5 0 1 1 0-3 1.5 1.5 0 0 1 0 3Z",
  sun: "M12 7a5 5 0 1 0 0 10 5 5 0 0 0 0-10Zm0-5v3m0 14v3M2 12h3m14 0h3M4.9 4.9 7 7m10 10 2.1 2.1m0-14.2L17 7M7 17l-2.1 2.1",
  moon: "M20 15.5A8.5 8.5 0 0 1 8.5 4 8.5 8.5 0 1 0 20 15.5Z",
  system: "M3 4h18v13H3V4Zm6 17h6m-3-4v4",
  logout: "M10 4H4v16h6m4-4 4-4-4-4m4 4H8",
  check: "m5 12 4 4L19 6",
  close: "M6 6l12 12M18 6 6 18"
};

export function AppIcon({ name, ...props }: SVGProps<SVGSVGElement> & { name: AppIconName }) {
  const stroked = name === "sun" || name === "moon" || name === "system"
    || name === "logout" || name === "check" || name === "close";
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" {...props}>
      <path
        d={paths[name]}
        fill={stroked ? "none" : "currentColor"}
        stroke={stroked ? "currentColor" : "none"}
        strokeWidth={stroked ? 2 : undefined}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
