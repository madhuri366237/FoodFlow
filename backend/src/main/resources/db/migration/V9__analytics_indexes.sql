-- =====================================================================
-- V9: index for the admin dashboard.
--
-- Customer and owner dashboards filter by user_id / restaurant_id, already served by
-- idx_orders_user_created and idx_orders_restaurant_created (V5). The ADMIN dashboard's
-- "last 7 days" chart filters ALL orders by created_at only; without this index that is a
-- full scan of the orders table on every dashboard load.
-- =====================================================================

CREATE INDEX idx_orders_created_at ON orders (created_at);
