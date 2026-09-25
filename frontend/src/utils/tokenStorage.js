// Where the JWT lives between page loads.
//
// localStorage keeps the user logged in across reloads and tabs. Trade-off: any script running
// on the page can read it, so the app must never inject untrusted HTML (React escapes all
// rendered text, and we never use dangerouslySetInnerHTML). An httpOnly cookie would hide the
// token from JavaScript, but needs CSRF protection and backend cookie handling. That is a
// reasonable later upgrade.
//
// Every access is wrapped in try/catch: storage can be unavailable (private mode, blocked
// site data), and the app must still work, just without remembering the session.

const TOKEN_KEY = 'foodflow.token';

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function saveToken(token) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Storage unavailable: the session lasts until the tab is closed.
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to clear.
  }
}

// Reads the payload ({ sub, email, role, exp, ... }). This does NOT verify the signature;
// only the backend can do that. It is used purely for UX, e.g. logging out when "exp" passes.
export function decodeToken(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

export function isTokenExpired(token, skewSeconds = 30) {
  const payload = decodeToken(token);
  return !payload?.exp || payload.exp * 1000 <= Date.now() + skewSeconds * 1000;
}
