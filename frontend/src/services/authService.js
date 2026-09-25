import apiClient from './apiClient.js';

// POST /api/auth/register -> { accessToken, tokenType, expiresInSeconds, user }
export async function register({ name, email, password, phone, accountType }) {
  const response = await apiClient.post('/auth/register', {
    name,
    email,
    password,
    phone: phone || null,
    accountType,
  });
  return response.data;
}

// POST /api/auth/login -> same shape as register
export async function login(email, password) {
  const response = await apiClient.post('/auth/login', { email, password });
  return response.data;
}

// GET /api/users/me: used to restore the session after a page reload.
export async function getCurrentUser() {
  const response = await apiClient.get('/users/me');
  return response.data;
}
