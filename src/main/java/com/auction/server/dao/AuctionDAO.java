package com.auction.server.dao;

import com.auction.domain.AuctionSession;
import com.auction.domain.AuctionStatus;
import com.auction.domain.BidTransaction;
import com.auction.server.util.DatabaseUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý mọi thao tác database liên quan đến phiên đấu giá và lịch sử đặt giá.
 *
 * <p>Tất cả phương thức dùng try-with-resources để đảm bảo
 * Connection được trả về HikariCP pool sau khi dùng xong.</p>
 */
public class AuctionDAO {

    /** Formatter cho datetime lưu vào MySQL (không dùng 'T' mà dùng dấu cách). */
    private static final DateTimeFormatter MYSQL_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // -------------------------------------------------------------------------
    // Phiên đấu giá
    // -------------------------------------------------------------------------

    /**
     * Lưu hoặc cập nhật phiên đấu giá (INSERT ... ON DUPLICATE KEY UPDATE).
     */
    public void saveSession(AuctionSession session) throws SQLException {
        String sql = """
                INSERT INTO auction_sessions
                    (auction_id, item_id, seller_id, start_time, end_time, status, winner_id, current_highest_bid)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    start_time          = VALUES(start_time),
                    end_time            = VALUES(end_time),
                    status              = VALUES(status),
                    winner_id           = VALUES(winner_id),
                    current_highest_bid = VALUES(current_highest_bid)
                """;

        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, session.getAuctionID());
            stmt.setString(2, session.getItemID());
            stmt.setString(3, session.getSellerID());
            stmt.setString(4, formatDateTime(session.getStartTime()));
            stmt.setString(5, formatDateTime(session.getEndTime()));
            stmt.setString(6, session.getStatus().name());
            stmt.setString(7, session.getWinnerID());
            stmt.setDouble(8, session.getCurrentHighestBid());

            stmt.executeUpdate();
        }
    }

    /**
     * Tải tất cả phiên đấu giá từ DB (JOIN với bảng items để lấy tên sản phẩm).
     */
    public List<AuctionSession> getAllSessions() throws SQLException {
        String sql = """
                SELECT s.auction_id, s.item_id,
                       COALESCE(i.name, s.item_id) AS item_name,
                       s.seller_id, s.start_time, s.end_time,
                       s.status, s.winner_id, s.current_highest_bid
                FROM   auction_sessions s
                LEFT JOIN items i ON s.item_id = i.id
                """;

        List<AuctionSession> sessions = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                AuctionSession session = new AuctionSession(
                        rs.getString("auction_id"),
                        rs.getString("item_id"),
                        rs.getString("item_name"),
                        rs.getString("seller_id"),
                        rs.getDouble("current_highest_bid"),
                        parseDateTime(rs.getString("start_time")),
                        parseDateTime(rs.getString("end_time")));

                session.setStatus(AuctionStatus.fromDbString(rs.getString("status")));
                session.setWinnerID(rs.getString("winner_id"));

                sessions.add(session);
            }
        }
        return sessions;
    }

    /**
     * Lấy tất cả phiên đấu giá của một seller theo sellerId.
     */
    public List<AuctionSession> getSessionsBySeller(String sellerId) throws SQLException {
        String sql = """
                SELECT s.auction_id, s.item_id,
                       COALESCE(i.name, s.item_id) AS item_name,
                       s.seller_id, s.start_time, s.end_time,
                       s.status, s.winner_id, s.current_highest_bid
                FROM   auction_sessions s
                LEFT JOIN items i ON s.item_id = i.id
                WHERE  s.seller_id = ?
                """;

        List<AuctionSession> sessions = new ArrayList<>();
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, sellerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    AuctionSession session = new AuctionSession(
                            rs.getString("auction_id"),
                            rs.getString("item_id"),
                            rs.getString("item_name"),
                            rs.getString("seller_id"),
                            rs.getDouble("current_highest_bid"),
                            parseDateTime(rs.getString("start_time")),
                            parseDateTime(rs.getString("end_time")));
                    session.setStatus(AuctionStatus.fromDbString(rs.getString("status")));
                    session.setWinnerID(rs.getString("winner_id"));
                    sessions.add(session);
                }
            }
        }
        return sessions;
    }

    /**
     * Xóa một phiên đấu giá theo auctionId.
     * Chỉ nên xóa khi chưa có bid (được kiểm soát ở tầng service/handler).
     */
    public void deleteSession(String auctionId) throws SQLException {
        String sql = "DELETE FROM auction_sessions WHERE auction_id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            stmt.executeUpdate();
        }
    }

    /**
     * Xóa tất cả các giao dịch đặt giá liên quan đến phiên đấu giá theo auctionId.
     */
    public void deleteBidsByAuction(String auctionId) throws SQLException {
        String sql = "DELETE FROM bid_transactions WHERE auction_id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            stmt.executeUpdate();
        }
    }


    /**
     * Cập nhật tên vật phẩm hiển thị của phiên (thông qua bảng items).
     */
    public void updateItemName(String itemId, String newName) throws SQLException {
        String sql = "UPDATE items SET name = ? WHERE id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newName);
            stmt.setString(2, itemId);
            stmt.executeUpdate();
        }
    }

    /**
     * Kiểm tra xem phiên đấu giá đã có bid chưa.
     * @return số lượng bid đã đặt
     */
    public int countBids(String auctionId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM bid_transactions WHERE auction_id = ?";
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, auctionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Giao dịch đặt giá
    // -------------------------------------------------------------------------

    /**
     * Lưu một giao dịch đặt giá vào bảng bid_transactions.
     */
    public void saveBidTransaction(BidTransaction tx) throws SQLException {
        String sql = """
                INSERT INTO bid_transactions (auction_id, bidder_id, bid_amount, bid_time)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, tx.getAuctionID());
            stmt.setString(2, tx.getBidderID());
            stmt.setDouble(3, tx.getBidAmount());
            stmt.setString(4, formatDateTime(tx.getBidTime()));

            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private String formatDateTime(LocalDateTime dt) {
        return (dt != null) ? dt.format(MYSQL_DT) : null;
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) return LocalDateTime.now();
        try {
            // MySQL trả về "yyyy-MM-dd HH:mm:ss"
            return LocalDateTime.parse(raw.trim(), MYSQL_DT);
        } catch (Exception e1) {
            try {
                // Fallback: ISO format "yyyy-MM-ddTHH:mm:ss"
                return LocalDateTime.parse(raw.trim());
            } catch (Exception e2) {
                System.err.println("[DAO] Không parse được datetime: " + raw);
                return LocalDateTime.now();
            }
        }
    }
}
