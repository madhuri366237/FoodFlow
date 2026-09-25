# FoodFlow Frontend

React 19 single-page app built with Vite. The project overview is in the [root README](../README.md).

## Commands

| Command | What it does |
|---|---|
| `npm install` | Install dependencies |
| `npm run dev` | Dev server on http://localhost:5173 (proxies `/api` to http://localhost:8080) |
| `npm run build` | Production build into `dist/` (owner and admin pages are split into separate chunks) |
| `npm run preview` | Serve the production build locally |

## How it talks to the backend

- **One Axios instance** (`src/services/apiClient.js`). Its request interceptor attaches `Authorization: Bearer <jwt>`. Its response interceptor turns any 401 into a logout (for example an expired token, or an account disabled by an admin).
- **Relative URLs only** (`/api/...`). Vite (dev) or Nginx (Docker) proxies them, so the browser sees one origin and no CORS is involved.
- **Services** in `src/services/` are the only code that knows URLs. Pages call functions such as `orderService.placeOrder(...)`.
- **The server is the source of truth.** The UI never computes prices or totals, and never decides permissions. It displays what the API returns, including `allowedTransitions` for the order action buttons.
- **Errors** come back as `{ status, error, message, fieldErrors }`. `src/utils/errors.js` turns them into messages; forms show `fieldErrors` next to each input.

## Folders

| Folder | Responsibility |
|---|---|
| `routes/` | URL → page map; `ProtectedRoute` (login and role guard; this is UX, the backend enforces the real rules) |
| `context/` | `AuthContext` (session, token expiry, logout on 401) and `CartContext` (the one shared cart) |
| `services/` | Every HTTP call, one module per resource |
| `pages/`, `pages/owner/`, `pages/admin/` | One component per screen, per role |
| `components/` | Reusable pieces: stat tiles, revenue chart, pagination, error boundary |
| `hooks/useApi.js` | Load-on-mount with loading and error state, ignoring stale responses |
| `utils/` | Formatting (₹, dates), token storage, role home pages |

## Security notes

- The JWT is stored in `localStorage` so sessions survive reloads. That makes XSS the main risk, so: React escapes all output, there's no `dangerouslySetInnerHTML`, and Nginx sends a strict **Content-Security-Policy** (`security-headers.conf`) so injected scripts can't run.
- In Docker, Nginx also sends `X-Content-Type-Options`, `X-Frame-Options: DENY` and a `Referrer-Policy`, and hides its version.
