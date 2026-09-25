import apiClient from './apiClient.js';

// Drops empty filters so the URL stays clean: ?keyword=biryani&page=0 instead of ?keyword=biryani&minRating=&...
function compact(params) {
  return Object.fromEntries(
    Object.entries(params).filter(([, value]) => value !== undefined && value !== null && value !== ''),
  );
}

// GET /api/restaurants?keyword&categoryId&minRating&open&page&size&sort -> PageResponse
export async function searchRestaurants(params) {
  const response = await apiClient.get('/restaurants', { params: compact(params) });
  return response.data;
}

export async function getRestaurant(id) {
  const response = await apiClient.get(`/restaurants/${id}`);
  return response.data;
}

// Available dishes only (the owner's full menu comes in Phase 11).
export async function getMenu(restaurantId) {
  const response = await apiClient.get(`/restaurants/${restaurantId}/menu`);
  return response.data;
}

export async function getCategories() {
  const response = await apiClient.get('/categories');
  return response.data;
}

export async function getReviews(restaurantId, page = 0, size = 5) {
  const response = await apiClient.get(`/restaurants/${restaurantId}/reviews`, { params: { page, size } });
  return response.data;
}

export async function createReview(restaurantId, { orderId, rating, comment }) {
  const response = await apiClient.post(`/restaurants/${restaurantId}/reviews`, { orderId, rating, comment });
  return response.data;
}
