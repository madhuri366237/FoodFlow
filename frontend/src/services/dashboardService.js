import apiClient from './apiClient.js';

// Every number on every dashboard is computed by PostgreSQL (AnalyticsQueries on the backend);
// the browser only displays it.

export async function getCustomerDashboard() {
  const response = await apiClient.get('/users/me/dashboard');
  return response.data;
}

export async function getOwnerDashboard(restaurantId) {
  const response = await apiClient.get('/owner/dashboard', { params: restaurantId ? { restaurantId } : {} });
  return response.data;
}

export async function getAdminAnalytics() {
  const response = await apiClient.get('/admin/analytics');
  return response.data;
}
