package com.auction.server.dao;

import com.auction.common.models.Admin;
import com.auction.common.models.Bidder;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.server.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO xử lý mọi thao tác database liên quan đến User.
 *
 * <p>Dùng try-with-resources để đảm bảo Connection luôn được trả về
 * HikariCP pool sau khi dùng xong.</p>
 *
 * <p>Cột {@code frozen_amount} trong bảng {@code users} lưu tiền đang
 * bị tạm khóa của Bidder. Khi restore từ DB, dùng
 * {@code wallet.restoreBalance()} và {@code wallet.restoreFrozenAmount()}
 * để thiết lập trực tiếp — không gọi freeze() vì balance trong DB
 * đã là số dư khả dụng (sau khi đã trừ frozen).</p>
 */
public class UserDAO {

    // -------------------------------------------------------------------------
    // Lưu / Cập nhật user
    // -------------------------------------------------------------------------

    /**
     * Lưu hoặc cập nhật user vào DB (INSERT ... ON DUPLICATE KEY UPDATE).
     * Với Bidder, lưu cả {@code balance} và {@code frozen_amount}.
     */
    public void saveUser(User user) throws SQLException {
        String sql = """
                INSERT INTO users
                    (id, username, password, full_name, email, role, balance, frozen_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    username      = VALUES(username),
                    password      = VALUES(password),
                    full_name     = VALUES(full_name),
                    email         = VALUES(email),
                    role          = VALUES(role),
                    balance       = VALUES(balance),
                    frozen_amount = VALUES(frozen_amount)
                """;

        double balance = 0, frozenAmount = 0;
        if (user instanceof Bidder bidder) {
            balance      = bidder.getWallet().getBalance();
            frozenAmount = bidder.getWallet().getFrozenAmount();
        }

        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getPassword());
            stmt.setString(4, user.getFullName());
            stmt.setString(5, user.getEmail());
            stmt.setString(6, user.getRole());
            stmt.setDouble(7, balance);
            stmt.setDouble(8, frozenAmount);

            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Tìm user
    // -------------------------------------------------------------------------

    /**
     * Tìm user theo username.
     *
     * @return User hoặc null nếu không tồn tại
     */
    public User getUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapRowToUser(rs) : null;
            }
        }
    }

    /**
     * Tìm user theo ID.
     *
     * @return User hoặc null nếu không tồn tại
     */
    public User findById(String id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapRowToUser(rs) : null;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Ánh xạ ResultSet → User
    // -------------------------------------------------------------------------

    /**
     * Chuyển một hàng ResultSet thành đối tượng User đúng loại.
     *
     * <p>Với Bidder: dùng {@code restoreBalance} và {@code restoreFrozenAmount}
     * để khôi phục trạng thái Wallet chính xác từ DB, thay vì gọi
     * {@code freeze()} (vì balance trong DB đã là số dư sau khi trừ frozen).</p>
     */
    private User mapRowToUser(ResultSet rs) throws SQLException {
        String id           = rs.getString("id");
        String username     = rs.getString("username");
        String password     = rs.getString("password");
        String fullName     = rs.getString("full_name");
        String email        = rs.getString("email");
        String role         = rs.getString("role");
        double balance      = rs.getDouble("balance");
        double frozenAmount = rs.getDouble("frozen_amount");

        return switch (role.toUpperCase()) {
            case "ADMIN"  -> new Admin(id, username, password, fullName, email);
            case "SELLER" -> new Seller(id, username, password, fullName, email);
            case "BIDDER" -> {
                // Khởi tạo Bidder với balance=0, sau đó restore trực tiếp từ DB
                Bidder bidder = new Bidder(id, username, password, fullName, email, 0.0);
                bidder.getWallet().restoreBalance(balance);
                bidder.getWallet().restoreFrozenAmount(frozenAmount);
                yield bidder;
            }
            default -> throw new SQLException("Vai trò không hợp lệ: " + role);
        };
    }
}
