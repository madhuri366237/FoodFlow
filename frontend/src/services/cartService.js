import apiClient from './apiClient.js';

// Every call returns the whole cart, priced by the server. The client sends only
// "which dish, how many"; it never sends a price.

export async function getCart() {
  const response = await apiClient.get('/cart');
  return response.data;
}

export async function addItem(menuItemId, quantity) {
  const response = await apiClient.post('/cart/items', { menuItemId, quantity });
  return response.data;
}

export async function updateQuantity(cartItemId, quantity) {
  const response = await apiClient.put(`/cart/items/${cartItemId}`, { quantity });
  return response.data;
}

export async function removeItem(cartItemId) {
  const response = await apiClient.delete(`/cart/items/${cartItemId}`);
  return response.data;
}

export async function clearCart() {
  await apiClient.delete('/cart');
}
