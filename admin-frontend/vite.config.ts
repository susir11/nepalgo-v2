import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// Dev server proxies every /api call to the API gateway (:8080), so the browser
// only ever talks to ONE origin: no CORS setup needed, and the same relative
// URLs work in production when the admin app is served behind the gateway/CDN.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: { "/api": { target: "http://localhost:8080", changeOrigin: true } },
  },
});
