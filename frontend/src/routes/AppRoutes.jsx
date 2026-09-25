import { lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout.jsx';
import ProtectedRoute from './ProtectedRoute.jsx';
import HomePage from '../pages/HomePage.jsx';
import LoginPage from '../pages/LoginPage.jsx';
import RegisterPage from '../pages/RegisterPage.jsx';
import RestaurantsPage from '../pages/RestaurantsPage.jsx';
import RestaurantDetailPage from '../pages/RestaurantDetailPage.jsx';
import CartPage from '../pages/CartPage.jsx';
import CheckoutPage from '../pages/CheckoutPage.jsx';
import PaymentPage from '../pages/PaymentPage.jsx';
import OrdersPage from '../pages/OrdersPage.jsx';
import OrderDetailPage from '../pages/OrderDetailPage.jsx';
import ProfilePage from '../pages/ProfilePage.jsx';
import CustomerDashboardPage from '../pages/CustomerDashboardPage.jsx';
import NotFoundPage from '../pages/NotFoundPage.jsx';

// Owner and admin pages are split into separate bundles, downloaded only when such a user
// opens them. Customers (most visitors) never download dashboard code.
const OwnerDashboardPage = lazy(() => import('../pages/owner/OwnerDashboardPage.jsx'));
const OwnerRestaurantPage = lazy(() => import('../pages/owner/OwnerRestaurantPage.jsx'));
const OwnerOrdersPage = lazy(() => import('../pages/owner/OwnerOrdersPage.jsx'));
const OwnerOrderDetailPage = lazy(() => import('../pages/owner/OwnerOrderDetailPage.jsx'));
const AdminDashboardPage = lazy(() => import('../pages/admin/AdminDashboardPage.jsx'));
const AdminUsersPage = lazy(() => import('../pages/admin/AdminUsersPage.jsx'));
const AdminRestaurantsPage = lazy(() => import('../pages/admin/AdminRestaurantsPage.jsx'));
const AdminOrdersPage = lazy(() => import('../pages/admin/AdminOrdersPage.jsx'));
const AdminCouponsPage = lazy(() => import('../pages/admin/AdminCouponsPage.jsx'));

/**
 * Route map. Each role has its own area; ProtectedRoute redirects anonymous users to /login
 * and shows "No access" for the wrong role. The backend enforces the same rules on every API call.
 */
export default function AppRoutes() {
  return (
    <Routes>
      <Route element={<MainLayout />}>
        <Route index element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="restaurants" element={<RestaurantsPage />} />
        <Route path="restaurants/:id" element={<RestaurantDetailPage />} />

        <Route element={<ProtectedRoute />}>
          <Route path="profile" element={<ProfilePage />} />
        </Route>

        <Route element={<ProtectedRoute roles={['CUSTOMER']} />}>
          <Route path="dashboard" element={<CustomerDashboardPage />} />
          <Route path="cart" element={<CartPage />} />
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="orders/:id" element={<OrderDetailPage />} />
          <Route path="orders/:id/pay" element={<PaymentPage />} />
        </Route>

        <Route path="owner" element={<ProtectedRoute roles={['RESTAURANT_OWNER']} />}>
          <Route index element={<OwnerDashboardPage />} />
          <Route path="restaurants/:id" element={<OwnerRestaurantPage />} />
          <Route path="orders" element={<OwnerOrdersPage />} />
          <Route path="orders/:id" element={<OwnerOrderDetailPage />} />
        </Route>

        <Route path="admin" element={<ProtectedRoute roles={['ADMIN']} />}>
          <Route index element={<AdminDashboardPage />} />
          <Route path="users" element={<AdminUsersPage />} />
          <Route path="restaurants" element={<AdminRestaurantsPage />} />
          <Route path="orders" element={<AdminOrdersPage />} />
          <Route path="coupons" element={<AdminCouponsPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
