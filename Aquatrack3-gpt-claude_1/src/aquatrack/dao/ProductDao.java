package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.Product;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the products table. Every value goes in as a ? parameter. */
public class ProductDao {

    public List<Product> findAll(boolean activeOnly) throws SQLException {
        String sql = "SELECT product_id, product_name, category, unit_price, stock_qty, reorder_level, is_active, delivery_price "
                + "FROM products " + (activeOnly ? "WHERE is_active = 1 " : "")
                + "ORDER BY category, product_name";
        List<Product> products = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                products.add(read(rs));
            }
        }
        return products;
    }

    /** The product, active or archived, already using this name (ignoring case and extra spaces), or null. */
    public Product findByName(String name, int exceptProductId) throws SQLException {
        String sql = "SELECT product_id, product_name, category, unit_price, stock_qty, reorder_level, is_active, delivery_price "
                + "FROM products WHERE LOWER(TRIM(product_name)) = LOWER(TRIM(?)) AND product_id <> ? LIMIT 1";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, exceptProductId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    public void insert(String name, String category, BigDecimal price, int reorderLevel) throws SQLException {
        insert(name, category, price, null, reorderLevel);
    }

    public void insert(String name, String category, BigDecimal price, BigDecimal deliveryPrice, int reorderLevel)
            throws SQLException {
        validatePrices(name, category, price, deliveryPrice, reorderLevel);
        Database.update("INSERT INTO products (product_name, category, unit_price, delivery_price, reorder_level) VALUES (?, ?, ?, ?, ?)",
                name, category, price, Product.REFILL.equals(category) ? deliveryPrice : null, reorderLevel);
    }

    public void update(int productId, String name, String category, BigDecimal price, int reorderLevel)
            throws SQLException {
        update(productId, name, category, price, null, reorderLevel);
    }

    public void update(int productId, String name, String category, BigDecimal price,
                       BigDecimal deliveryPrice, int reorderLevel) throws SQLException {
        validatePrices(name, category, price, deliveryPrice, reorderLevel);
        if (Database.update("UPDATE products SET product_name = ?, category = ?, unit_price = ?, delivery_price = ?, reorder_level = ? "
                + "WHERE product_id = ?", name, category, price, Product.REFILL.equals(category) ? deliveryPrice : null,
                reorderLevel, productId) == 0 && Database.count("SELECT COUNT(*) FROM products WHERE product_id = ?", productId) == 0) {
            throw new IllegalArgumentException("That product no longer exists. Reload the list.");
        }
    }

    /** True when at least one sale uses this product. Such products can be archived but not deleted. */
    public boolean hasOrders(int productId) throws SQLException {
        return Database.count("SELECT COUNT(*) FROM order_items WHERE product_id = ?", productId) > 0;
    }

    /** Removes a product that has never been sold. Callers check hasOrders first. */
    public void delete(int productId) throws SQLException {
        Database.update("DELETE FROM products WHERE product_id = ?", productId);
    }

    /** Archives (active = false) or restores (active = true) a product. Old orders keep their item names. */
    public void setActive(int productId, boolean active) throws SQLException {
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement("SELECT product_name, category, unit_price, delivery_price, reorder_level "
                        + "FROM products WHERE product_id = ? FOR UPDATE")) {
                    ps.setInt(1, productId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("That product no longer exists.");
                        if (active) validatePrices(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
                                rs.getBigDecimal(4), rs.getInt(5));
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE products SET is_active = ? WHERE product_id = ?")) {
                    ps.setBoolean(1, active);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Adds stock that has been received. */
    public void restock(int productId, int quantity) throws SQLException {
        if (quantity <= 0) throw new IllegalArgumentException("Stock received must be more than zero.");
        if (Database.update("UPDATE products SET stock_qty = stock_qty + ? WHERE product_id = ? "
                + "AND is_active = 1 AND category <> 'Refill'", quantity, productId) == 0) {
            throw new IllegalArgumentException("This item is no longer available for restocking. Reload inventory.");
        }
    }

    public void setReorderLevel(int productId, int level) throws SQLException {
        if (level < 0) throw new IllegalArgumentException("Low-stock alert cannot be negative.");
        if (Database.update("UPDATE products SET reorder_level = ? WHERE product_id = ? AND is_active = 1 AND category <> 'Refill'", level, productId) == 0
                && Database.count("SELECT COUNT(*) FROM products WHERE product_id = ? AND is_active = 1 AND category <> 'Refill'", productId) == 0) {
            throw new IllegalArgumentException("This item is no longer available. Reload inventory.");
        }
    }

    private static void validatePrices(String name, String category, BigDecimal price,
                                       BigDecimal deliveryPrice, int level) {
        if (!Product.REFILL.equals(category) && !Product.CONTAINER.equals(category) && !Product.CAP_SEAL.equals(category))
            throw new IllegalArgumentException("Choose a valid product category.");
        if (level < 0) throw new IllegalArgumentException("Low-stock alert cannot be negative.");
        Product product = new Product(0, name, category, price, deliveryPrice, 0, level, true);
        product.priceForOrderType("Walk-in");
        if (Product.REFILL.equals(category)) product.priceForOrderType("Delivery");
    }

    private static Product read(ResultSet rs) throws SQLException {
        return new Product(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getBigDecimal(8), rs.getInt(5), rs.getInt(6), rs.getBoolean(7));
    }
}
