import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// The React app always calls the backend with relative URLs ("/api/...").
// In development, Vite's dev server forwards those requests to Spring Boot,
// so the browser sees a single origin and no CORS preflight is needed.
// In Docker (Phase 14), Nginx will play the same proxy role.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backendUrl = env.VITE_PROXY_TARGET || 'http://localhost:8080';

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': { target: backendUrl, changeOrigin: true },
        '/actuator': { target: backendUrl, changeOrigin: true },
      },
    },
  };
});
