-- AquaTrack version 3: fresh-install schema for MySQL 8.0.16+ / MariaDB 10.4+.
-- No application users or sample transactions are inserted here.
-- Existing tables are left untouched: this is NOT an upgrade/migration script.
-- Back up an existing database before making any schema changes.

CREATE DATABASE IF NOT EXISTS aquatrack
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aquatrack;

CREATE TABLE IF NOT EXISTS users (
    user_id INT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role ENUM('Staff', 'Owner/Admin') NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_username (username),
    KEY ix_users_full_name (full_name),
    CONSTRAINT chk_users_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS customers (
    customer_id INT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    contact_no VARCHAR(11) NOT NULL,
    is_archived TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (customer_id),
    KEY ix_customers_name (full_name),
    KEY ix_customers_contact (contact_no),
    KEY ix_customers_active_name (is_archived, full_name),
    CONSTRAINT chk_customers_archived CHECK (is_archived IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS products (
    product_id INT NOT NULL AUTO_INCREMENT,
    product_name VARCHAR(100) NOT NULL,
    category ENUM('Refill', 'Container', 'Cap/Seal') NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    stock_qty INT NOT NULL DEFAULT 0,
    reorder_level INT NOT NULL DEFAULT 0,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (product_id),
    KEY ix_products_name (product_name),
    KEY ix_products_active_category (is_active, category),
    CONSTRAINT chk_products_price CHECK (unit_price > 0),
    CONSTRAINT chk_products_stock CHECK (stock_qty >= 0),
    CONSTRAINT chk_products_reorder CHECK (reorder_level >= 0),
    CONSTRAINT chk_products_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS delivery_staff (
    staff_id INT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    contact_no VARCHAR(11) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    PRIMARY KEY (staff_id),
    KEY ix_delivery_staff_active_name (is_active, full_name),
    CONSTRAINT chk_delivery_staff_active CHECK (is_active IN (0, 1))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
    order_id INT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    order_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    order_type ENUM('Walk-in', 'Delivery') NOT NULL,
    payment_type ENUM('Cash', 'Credit') NOT NULL,
    total_amount DECIMAL(20,2) NOT NULL,
    delivery_status ENUM('Pending', 'Out for Delivery', 'Delivered') NOT NULL,
    staff_id INT NULL,
    created_by INT NOT NULL,
    PRIMARY KEY (order_id),
    KEY ix_orders_date (order_date, order_id),
    KEY ix_orders_customer_payment (customer_id, payment_type),
    KEY ix_orders_delivery (order_type, delivery_status, order_date),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT fk_orders_staff FOREIGN KEY (staff_id) REFERENCES delivery_staff (staff_id),
    CONSTRAINT fk_orders_user FOREIGN KEY (created_by) REFERENCES users (user_id),
    CONSTRAINT chk_orders_total CHECK (total_amount >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_items (
    item_id INT NOT NULL AUTO_INCREMENT,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(20,2) NOT NULL,
    PRIMARY KEY (item_id),
    KEY ix_order_items_order (order_id, item_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (product_id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_order_items_price CHECK (unit_price > 0),
    CONSTRAINT chk_order_items_total CHECK (line_total >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payments (
    payment_id INT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (payment_id),
    KEY ix_payments_customer (customer_id),
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT chk_payments_amount CHECK (amount > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS container_ledger (
    ledger_id INT NOT NULL AUTO_INCREMENT,
    customer_id INT NOT NULL,
    movement ENUM('Borrowed', 'Returned') NOT NULL,
    quantity INT NOT NULL,
    order_id INT NULL,
    movement_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ledger_id),
    KEY ix_container_ledger_customer (customer_id),
    KEY ix_container_ledger_order (order_id),
    CONSTRAINT fk_container_ledger_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id),
    CONSTRAINT fk_container_ledger_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT chk_container_ledger_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

-- Foreign keys intentionally use RESTRICT/NO ACTION (the default): deleting a
-- parent must not erase sales, payment, or container history. Use app archiving.
