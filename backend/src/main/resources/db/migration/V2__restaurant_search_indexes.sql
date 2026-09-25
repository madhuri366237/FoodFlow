-- =====================================================================
-- V2: indexes for restaurant keyword search.
--
-- The search runs   lower(name) LIKE '%biryani%'   on restaurant names and dish names.
-- A normal B-tree index can only help a LIKE whose pattern has a fixed prefix
-- ('biryani%'), never one with a leading wildcard ('%biryani%'), so without
-- these indexes every search is a full table scan.
--
-- pg_trgm splits text into 3-character pieces ("bir", "iry", "rya", ...).
-- A GIN index over those pieces lets PostgreSQL find rows containing a
-- substring anywhere in the text.
--
-- The indexes are on lower(name) because the query compares lower(name);
-- an index on plain "name" would not be used for that expression.
-- =====================================================================

-- pg_trgm ships with PostgreSQL and is a "trusted" extension (PG 13+):
-- the database owner can enable it without superuser rights.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_restaurants_name_trgm ON restaurants USING gin (lower(name) gin_trgm_ops);

CREATE INDEX idx_menu_items_name_trgm ON menu_items USING gin (lower(name) gin_trgm_ops);
