-- =====================================================================
-- V10 (audit): index a foreign key whose referenced rows can be deleted.
--
-- carts.restaurant_id -> restaurants ON DELETE SET NULL. When a restaurant is deleted,
-- PostgreSQL must find every cart pointing at it. PostgreSQL never indexes the referencing
-- side of a foreign key automatically, so without this the delete scans all carts.
-- Partial: empty carts (restaurant_id IS NULL) never need to be found this way.
--
-- Checked and deliberately NOT indexed: reviews.user_id, order_status_history.changed_by.
-- Users are never deleted (only disabled), and no query filters by those columns.
-- =====================================================================

CREATE INDEX idx_carts_restaurant_id ON carts (restaurant_id) WHERE restaurant_id IS NOT NULL;
