import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import * as authService from '../services/authService.js';
import { SESSION_EXPIRED_EVENT } from '../services/apiClient.js';
import { clearToken, decodeToken, getToken, isTokenExpired, saveToken } from '../utils/tokenStorage.js';

const AuthContext = createContext(null);

// setTimeout breaks for delays above ~24.8 days (2^31 ms); tokens here live 24 h anyway.
const MAX_TIMER_MS = 2_147_483_647;

/**
 * The session, available to every component through useAuth().
 *
 * status: 'loading'       - on page load, while an existing token is being checked
 *         'authenticated' - user is set
 *         'anonymous'     - not logged in
 *
 * The session ends when:
 *   - the user clicks Logout,
 *   - the token's "exp" time passes (a timer fires), or
 *   - any API call answers 401 (apiClient's interceptor fires SESSION_EXPIRED_EVENT),
 *     e.g. the account was disabled by an admin.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('loading');
  const expiryTimer = useRef(null);

  const logout = useCallback(() => {
    clearToken();
    clearTimeout(expiryTimer.current);
    setUser(null);
    setStatus('anonymous');
  }, []);

  const startSession = useCallback(
    (token, sessionUser) => {
      saveToken(token);
      setUser(sessionUser);
      setStatus('authenticated');
      clearTimeout(expiryTimer.current);
      const exp = decodeToken(token)?.exp;
      if (exp) {
        expiryTimer.current = setTimeout(logout, Math.min(exp * 1000 - Date.now(), MAX_TIMER_MS));
      }
    },
    [logout],
  );

  // Page reload: the token survived in localStorage, but is it still accepted? Ask the backend.
  useEffect(() => {
    const token = getToken();
    if (!token || isTokenExpired(token)) {
      clearToken();
      setStatus('anonymous');
      return;
    }
    authService
      .getCurrentUser()
      .then((currentUser) => startSession(token, currentUser))
      .catch(() => logout());
  }, [startSession, logout]);

  useEffect(() => {
    window.addEventListener(SESSION_EXPIRED_EVENT, logout);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, logout);
  }, [logout]);

  useEffect(() => () => clearTimeout(expiryTimer.current), []);

  const login = useCallback(
    async (email, password) => {
      const result = await authService.login(email, password);
      startSession(result.accessToken, result.user);
      return result.user;
    },
    [startSession],
  );

  const register = useCallback(
    async (form) => {
      const result = await authService.register(form);
      startSession(result.accessToken, result.user);
      return result.user;
    },
    [startSession],
  );

  const value = useMemo(
    () => ({
      user,
      status,
      isAuthenticated: status === 'authenticated',
      hasRole: (...roles) => status === 'authenticated' && roles.includes(user.role),
      login,
      register,
      logout,
    }),
    [user, status, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
