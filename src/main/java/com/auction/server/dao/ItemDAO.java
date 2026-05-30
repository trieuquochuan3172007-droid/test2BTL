package com.auction.server.dao;

import com.auction.common.models.Item;
import com.auction.common.pattern.ItemFactory;
import com.auction.server.util.DatabaseUtil;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý mọi thao tác database liên quan đến Item.
 *
 * <p>Dùng {@link ItemFactory} (Factory Method Pattern) để tạo đối tượng Item
 * đúng loại khi đọc từ database, thay vì switch-case inline.</p>
 */
public class ItemDAO {

    /**
     * Lưu hoặc cập nhật sản phẩm (INSERT ... ON DUPLICATE KEY UPDATE).
     */
    public void saveItem(Item item) throws SQLException {
        String sql = """
                INSERT INTO items (id, name, description, init_price, category)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name        = VALUES(name),
                    description = VALUES(description),
                    init_price  = VALUES(init_price),
                    category    = VALUES(category)
                """;

        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, item.getId());
            stmt.setString(2, item.getName());
            stmt.setString(3, item.getDescription());
            stmt.setDouble(4, item.getInitPrice());
            stmt.setString(5, item.getCategory());

            stmt.executeUpdate();
        }
    }

    /**
     * Tìm sản phẩm theo ID.
     *
     * @return Item đúng loại hoặc null nếu không tồn tại
     */
    public Item findById(String id) throws SQLException {
        String sql = "SELECT * FROM items WHERE id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapRowToItem(rs) : null;
            }
        }
    }

    /**
     * Lấy tất cả sản phẩm.
     */
    public List<Item> getAllItems() throws SQLException {
        String sql = "SELECT * FROM items ORDER BY name";
        List<Item> items = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                items.add(mapRowToItem(rs));
            }
        }
        return items;
    }

    // -------------------------------------------------------------------------
    // Ánh xạ ResultSet → Item (dùng ItemFactory — Factory Method Pattern)
    // -------------------------------------------------------------------------

    /**
     * Tạo đối tượng Item từ một hàng trong ResultSet.
     * Dùng {@link ItemFactory} thay vì switch-case inline.
     */
    private Item mapRowToItem(ResultSet rs) throws SQLException {
        return ItemFactory.create(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getDouble("init_price"),
                rs.getString("category")
        );
    }
}
