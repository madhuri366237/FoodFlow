import apiClient from './apiClient.js';

// POST /api/orders: the server builds the order from the cart. We send only the choices.
export async function placeOrder({ addressId, paymentMethod, couponCode }) {
  const response = await apiClient.post('/orders', {
    addressId,
    paymentMethod,
    couponCode: couponCode || null,
  });
  return response.data;
}

export async function getMyOrders(page = 0, size = 10) {
  const response = await apiClient.get('/orders', { params: { page, size } });
  return response.data;
}

export async function getOrder(id) {
  const response = await apiClient.get(`/orders/${id}`);
  return response.data;
}

export async function cancelOrder(id, reason) {
  const response = await apiClient.post(`/orders/${id}/cancel`, { reason: reason || null });
  return response.data;
}
