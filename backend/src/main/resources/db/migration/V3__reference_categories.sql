-- =====================================================================
-- V3: reference data - the global food categories.
--
-- Categories are part of the product itself (every environment, including
-- production, needs the same list), so they belong in a migration.
-- Demo users, restaurants and orders are NOT reference data; they go into
-- separate development-only seed data later.
--
-- Admins can add more categories through /api/admin/categories.
-- =====================================================================

INSERT INTO categories (name, description) VALUES
    ('Biryani',      'Fragrant layered rice dishes'),
    ('Pizza',        'Oven-baked pizzas'),
    ('Burgers',      'Burgers and sliders'),
    ('South Indian', 'Dosa, idli, vada and more'),
    ('North Indian', 'Curries, breads and tandoor'),
    ('Chinese',      'Indo-Chinese favourites'),
    ('Starters',     'Snacks and appetisers'),
    ('Rolls',        'Rolls and wraps'),
    ('Desserts',     'Sweets and ice creams'),
    ('Beverages',    'Soft drinks, juices and shakes');
