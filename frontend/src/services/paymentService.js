import apiClient from './apiClient.js';

// POST /api/payments. In production, paymentToken comes from the gateway's own widget
// (Razorpay/Stripe), which collects card/UPI details in the browser. Our app never sees them.
// No amount is sent: the server charges the order's total.
export async function pay(orderId, paymentToken) {
  const response = await apiClient.post('/payments', { orderId, paymentToken });
  return response.data;
}

export async function getOrderPayments(orderId) {
  const response = await apiClient.get(`/orders/${orderId}/payments`);
  return response.data;
}
