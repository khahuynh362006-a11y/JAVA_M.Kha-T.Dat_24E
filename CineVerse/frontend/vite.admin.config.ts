import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath } from "node:url";
const root = fileURLToPath(new URL("../admin", import.meta.url));
export default defineConfig({
  root,
  publicDir: fileURLToPath(new URL("./public", import.meta.url)),
  cacheDir: fileURLToPath(new URL("./node_modules/.vite-admin", import.meta.url)),
  plugins: [react(), tailwindcss()],
  server: {
    host: "127.0.0.1",
    port: 5174,
    strictPort: true,
    fs: { allow: [fileURLToPath(new URL("../", import.meta.url))] },
    proxy: { "/api": { target: "http://127.0.0.1:8080", changeOrigin: false } },
  },
  preview: { host: "127.0.0.1", port: 5174 },
  build: { outDir: "dist", emptyOutDir: true },
});
