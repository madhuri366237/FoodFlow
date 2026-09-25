import axios from 'axios';
import { clearToken, getToken } from '../utils/tokenStorage.js';

// Fired when the backend says our token is no longer valid; AuthContext listens and logs out.
export const SESSION_EXPIRED_EVENT = 'foodflow:session-expired';

// The single Axios instance for every backend call.
// baseURL is relative: in development Vite proxies /api to Spring Boot (vite.config.js);
// in Docker, Nginx does the same. The browser only ever talks to one origin.
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// REQUEST interceptor: attach "Authorization: Bearer <jwt>" to every call when logged in.
// Components never touch the token themselves.
apiClient.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// RESPONSE interceptor: one central place to react to an expired or invalid token.
// A 401 from /auth/* is just "wrong password", so it is left to the login form.
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    if (status === 401 && !url.startsWith('/auth/')) {
      clearToken();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    return Promise.reject(error);
  },
);

export default apiClient;
