package com.auction.server.dao;

import com.auction.server.util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý bảng notifications.
 *
 * <p>Mỗi notification gồm:
 * <ul>
 *   <li>id – AUTO_INCREMENT</li>
 *   <li>user_id – người nhận</li>
 *   <li>type – loại (BID_OUTBID, AUCTION_WON, AUCTION_LOST, AUCTION_ENDING,
 *       ANTI_SNIPE, BID_PLACED, SESSION_CREATED, SESSION_ENDED)</li>
 *   <li>content – nội dung hiển thị</li>
 *   <li>auction_id – phiên liên quan (nullable)</li>
 *   <li>is_read – đã đọc chưa</li>
 *   <li>created_at – thời điểm tạo</li>
 * </ul>
 * </p>
 */
public class NotificationDAO {

    private static final DateTimeFormatter MYSQL_DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -------------------------------------------------------------------------
    // Tạo bảng (gọi từ AuctionServer.initializeDatabase)
    // -------------------------------------------------------------------------

    public void createTableIfNotExists() throws SQLException {
        String sql = """
                CREATE TABLE IF NOT EXISTS notifications (
                    id         INT AUTO_INCREMENT PRIMARY KEY,
                    user_id    VARCHAR(50)  NOT NULL,
                    type       VARCHAR(50)  NOT NULL,
                    content    TEXT         NOT NULL,
                    auction_id VARCHAR(50)  DEFAULT NULL,
                    is_read    TINYINT(1)   DEFAULT 0,
                    created_at DATETIME     NOT NULL
                )""";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             Statement stmt  = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    // -------------------------------------------------------------------------
    // Ghi notification
    // -------------------------------------------------------------------------

    /**
     * Lưu một notification mới vào DB.
     *
     * @param userId    ID người nhận
     * @param type      Loại (ví dụ "BID_OUTBID")
     * @param content   Nội dung hiển thị
     * @param auctionId ID phiên liên quan (có thể null)
     */
    public void save(String userId, String type, String content, String auctionId)
            throws SQLException {
        String sql = """
                INSERT INTO notifications (user_id, type, content, auction_id, is_read, created_at)
                VALUES (?, ?, ?, ?, 0, ?)
                """;
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.setString(2, type);
            stmt.setString(3, content);
            stmt.setString(4, auctionId);
            stmt.setString(5, LocalDateTime.now().format(MYSQL_DT));
            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Đọc notification
    // -------------------------------------------------------------------------

    /**
     * Lấy tất cả notification của một user, mới nhất trước.
     * Trả về List<String[]>: [id, type, content, auctionId, isRead, createdAt]
     */
    public List<String[]> getByUser(String userId) throws SQLException {
        String sql = """
                SELECT id, type, content, auction_id, is_read, created_at
                FROM   notifications
                WHERE  user_id = ?
                ORDER  BY created_at DESC
                LIMIT  50
                """;
        List<String[]> result = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(new String[]{
                            String.valueOf(rs.getInt("id")),
                            rs.getString("type"),
                            rs.getString("content"),
                            rs.getString("auction_id") != null ? rs.getString("auction_id") : "",
                            String.valueOf(rs.getInt("is_read")),
                            rs.getString("created_at")
                    });
                }
            }
        }
        return result;
    }

    /** Đếm số notification chưa đọc của user. */
    public int countUnread(String userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Đánh dấu đã đọc
    // -------------------------------------------------------------------------

    /** Đánh dấu tất cả notification của user là đã đọc. */
    public void markAllRead(String userId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);
            stmt.executeUpdate();
        }
    }

    /** Đánh dấu một notification cụ thể là đã đọc. */
    public void markRead(int notificationId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, notificationId);
            stmt.executeUpdate();
        }
    }
}
