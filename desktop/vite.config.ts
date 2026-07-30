import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(({ mode }) => {
  const environment = loadEnv(mode, process.cwd(), "");
  const backendUrl = environment.BILLING_BACKEND_URL ?? "http://127.0.0.1:8080";

  return {
    base: "./",
    plugins: [react(), tailwindcss()],
    server: {
      host: "127.0.0.1",
      port: 5173,
      strictPort: true,
      proxy: {
        "/api": {
          target: backendUrl,
          changeOrigin: false
        }
      }
    },
    build: {
      outDir: "dist",
      emptyOutDir: true,
      sourcemap: true
    }
  };
});

