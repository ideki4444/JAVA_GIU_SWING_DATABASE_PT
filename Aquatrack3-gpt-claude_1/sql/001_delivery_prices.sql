-- Run against the explicitly selected AquaTrack database after taking a backup.
-- No USE statement: the same script can be tested against an isolated database.
-- Existing unit_price values and all historical order items remain unchanged.
SET @aquatrack_has_delivery_price = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products' AND COLUMN_NAME = 'delivery_price'
);
SET @aquatrack_delivery_ddl = IF(@aquatrack_has_delivery_price > 0,
    'SELECT 1',
    'ALTER TABLE products ADD COLUMN delivery_price DECIMAL(10,2) NULL AFTER unit_price'
);
PREPARE aquatrack_delivery_stmt FROM @aquatrack_delivery_ddl;
EXECUTE aquatrack_delivery_stmt;
DEALLOCATE PREPARE aquatrack_delivery_stmt;

-- Initial delivery rates match each existing refill's price; the owner can edit them.
-- Rerunning never replaces a delivery price that has already been configured.
UPDATE products SET delivery_price = unit_price
WHERE category = 'Refill' AND delivery_price IS NULL;

SET @aquatrack_has_delivery_check = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
      AND CONSTRAINT_NAME = 'chk_products_delivery_price'
);
SET @aquatrack_delivery_ddl = IF(@aquatrack_has_delivery_check > 0,
    'SELECT 1',
    'ALTER TABLE products ADD CONSTRAINT chk_products_delivery_price CHECK (delivery_price IS NULL OR delivery_price >= 0)'
);
PREPARE aquatrack_delivery_stmt FROM @aquatrack_delivery_ddl;
EXECUTE aquatrack_delivery_stmt;
DEALLOCATE PREPARE aquatrack_delivery_stmt;
