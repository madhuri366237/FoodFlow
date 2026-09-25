import apiClient from './apiClient.js';

// Validated against the server-side cart; we don't send a subtotal.
// -> { code, description, subtotal, discount, total }
export async function validateCoupon(code) {
  const response = await apiClient.post('/coupons/validate', { code });
  return response.data;
}

export async function getAvailableCoupons() {
  const response = await apiClient.get('/coupons');
  return response.data;
}
