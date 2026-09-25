import apiClient from './apiClient.js';

export async function getAddresses() {
  const response = await apiClient.get('/addresses');
  return response.data;
}

export async function createAddress(address) {
  const response = await apiClient.post('/addresses', address);
  return response.data;
}

export async function makeDefault(id) {
  const response = await apiClient.patch(`/addresses/${id}/default`);
  return response.data;
}

export async function deleteAddress(id) {
  await apiClient.delete(`/addresses/${id}`);
}
