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
        String sql = "SELECT product_id, product_name, category, unit_price, stock_qty, reorder_level, is_active "
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
        String sql = "SELECT product_id, product_name, category, unit_price, stock_qty, reorder_level, is_active "
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
        Database.update("INSERT INTO products (product_name, category, unit_price, reorder_level) VALUES (?, ?, ?, ?)",
                name, category, price, reorderLevel);
    }

    public void update(int productId, String name, String category, BigDecimal price, int reorderLevel)
            throws SQLException {
        Database.update("UPDATE products SET product_name = ?, category = ?, unit_price = ?, reorder_level = ? "
                + "WHERE product_id = ?", name, category, price, reorderLevel, productId);
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
        Database.update("UPDATE products SET is_active = ? WHERE product_id = ?", active ? 1 : 0, productId);
    }

    /** Adds stock that has been received. */
    public void restock(int productId, int quantity) throws SQLException {
        Database.update("UPDATE products SET stock_qty = stock_qty + ? WHERE product_id = ?", quantity, productId);
    }

    public void setReorderLevel(int productId, int level) throws SQLException {
        Database.update("UPDATE products SET reorder_level = ? WHERE product_id = ?", level, productId);
    }

    private static Product read(ResultSet rs) throws SQLException {
        return new Product(rs.getInt(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getInt(5), rs.getInt(6), rs.getBoolean(7));
    }
}
