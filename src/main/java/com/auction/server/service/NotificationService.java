package com.auction.server.service;

import com.auction.server.dao.NotificationDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.network.AuctionServer;
import com.auction.common.models.User;

import java.util.List;

/**
 * Service tập trung toàn bộ logic tạo và gửi notification.
 *
 * <p>Luồng hoạt động:
 * <ol>
 *   <li>Ghi vào DB (NotificationDAO.save) để lưu lịch sử.</li>
 *   <li>Push realtime qua socket nếu user đang online
 *       ({@link AuctionServer#sendToUser}).</li>
 * </ol>
 * </p>
 *
 * <p>Format tin nhắn socket:
 * {@code NOTIFICATION|type|content|auctionId}
 * </p>
 *
 * <p>Được gọi từ {@link com.auction.server.network.ClientHandler} sau mỗi sự kiện
 * quan trọng (bid mới, kết thúc phiên, anti-snipe, v.v.).</p>
 */
public class NotificationService {

    // Loại notification — dùng làm hằng số để tránh typo
    public static final String TYPE_BID_OUTBID      = "BID_OUTBID";       // Bidder bị vượt mặt
    public static final String TYPE_AUCTION_WON     = "AUCTION_WON";      // Bidder thắng cuộc
    public static final String TYPE_AUCTION_LOST    = "AUCTION_LOST";     // Bidder thua cuộc
    public static final String TYPE_AUCTION_ENDING  = "AUCTION_ENDING";   // Phiên sắp kết thúc (5 phút)
    public static final String TYPE_ANTI_SNIPE      = "ANTI_SNIPE";       // Phiên bị gia hạn
    public static final String TYPE_BID_PLACED      = "BID_PLACED";       // Seller: có người đặt giá
    public static final String TYPE_SESSION_CREATED = "SESSION_CREATED";  // Seller: tạo phiên thành công
    public static final String TYPE_SESSION_ENDED   = "SESSION_ENDED";    // Seller: phiên kết thúc

    private final NotificationDAO notificationDAO;
    private final AuctionServer   server;

    public NotificationService(AuctionServer server) {
        this.notificationDAO = new NotificationDAO();
        this.server          = server;
    }

    // =========================================================================
    // API công khai — gọi từ ClientHandler / Scheduler
    // =========================================================================

    // -------------------------------------------------------------------------
    // BIDDER notifications
    // -------------------------------------------------------------------------

    /**
     * Bidder bị vượt mặt bởi người khác.
     *
     * @param outbidBidderId ID bidder bị vượt
     * @param auctionId      ID phiên
     * @param itemName       Tên sản phẩm
     * @param newPrice       Giá mới (của người vừa vượt)
     */
    public void notifyBidderOutbid(String outbidBidderId,
                                   String auctionId,
                                   String itemName,
                                   double newPrice) {
        String content = String.format(
                "Bạn vừa bị vượt mặt tại phiên \"%s\"! Giá mới: %,.0f ₫. Đặt giá ngay để giành lại!",
                itemName, newPrice);
        send(outbidBidderId, TYPE_BID_OUTBID, content, auctionId);
    }

    /**
     * Bidder thắng cuộc khi phiên kết thúc.
     *
     * @param winnerId   ID bidder thắng
     * @param auctionId  ID phiên
     * @param itemName   Tên sản phẩm
     * @param finalPrice Giá thắng cuộc
     */
    public void notifyBidderWon(String winnerId,
                                String auctionId,
                                String itemName,
                                double finalPrice) {
        String content = String.format(
                "🎉 Chúc mừng! Bạn đã thắng phiên \"%s\" với giá %,.0f ₫!",
                itemName, finalPrice);
        send(winnerId, TYPE_AUCTION_WON, content, auctionId);
    }

    /**
     * Bidder thua cuộc khi phiên kết thúc (đã từng đặt giá nhưng không thắng).
     *
     * @param loserId    ID bidder thua
     * @param auctionId  ID phiên
     * @param itemName   Tên sản phẩm
     * @param finalPrice Giá thắng cuộc (của người khác)
     */
    public void notifyBidderLost(String loserId,
                                  String auctionId,
                                  String itemName,
                                  double finalPrice) {
        String content = String.format(
                "Phiên \"%s\" đã kết thúc. Rất tiếc, bạn không thắng. Giá cuối: %,.0f ₫.",
                itemName, finalPrice);
        send(loserId, TYPE_AUCTION_LOST, content, auctionId);
    }

    /**
     * Cảnh báo phiên sắp kết thúc (còn 5 phút) — gửi cho tất cả bidder đang tham gia.
     *
     * @param bidderId  ID bidder
     * @param auctionId ID phiên
     * @param itemName  Tên sản phẩm
     */
    public void notifyAuctionEnding(String bidderId,
                                     String auctionId,
                                     String itemName) {
        String content = String.format(
                "⏰ Phiên \"%s\" còn dưới 5 phút! Đừng bỏ lỡ cơ hội.",
                itemName);
        send(bidderId, TYPE_AUCTION_ENDING, content, auctionId);
    }

    /**
     * Phiên bị gia hạn do anti-sniping — gửi cho bidder đang xem phiên.
     *
     * @param bidderId   ID bidder
     * @param auctionId  ID phiên
     * @param itemName   Tên sản phẩm
     * @param newEndTime Thời gian kết thúc mới (ISO string)
     */
    public void notifyAntiSnipe(String bidderId,
                                 String auctionId,
                                 String itemName,
                                 String newEndTime) {
        String content = String.format(
                "🔔 Phiên \"%s\" được gia hạn do có bid mới vào phút cuối! Kết thúc lúc: %s.",
                itemName, newEndTime);
        send(bidderId, TYPE_ANTI_SNIPE, content, auctionId);
    }

    // -------------------------------------------------------------------------
    // SELLER notifications
    // -------------------------------------------------------------------------

    /**
     * Seller vừa tạo phiên thành công.
     *
     * @param sellerId  ID seller
     * @param auctionId ID phiên vừa tạo
     * @param itemName  Tên sản phẩm
     */
    public void notifySellerSessionCreated(String sellerId,
                                            String auctionId,
                                            String itemName) {
        String content = String.format(
                "✅ Phiên đấu giá \"%s\" (ID: %s) đã được tạo thành công!",
                itemName, auctionId);
        send(sellerId, TYPE_SESSION_CREATED, content, auctionId);
    }

    /**
     * Có người đặt giá mới vào phiên của Seller.
     *
     * @param sellerId    ID seller
     * @param auctionId   ID phiên
     * @param itemName    Tên sản phẩm
     * @param bidderName  Username của bidder
     * @param newPrice    Giá vừa đặt
     */
    public void notifySellerNewBid(String sellerId,
                                    String auctionId,
                                    String itemName,
                                    String bidderName,
                                    double newPrice) {
        String content = String.format(
                "💰 \"%s\" vừa đặt %,.0f ₫ cho phiên \"%s\".",
                bidderName, newPrice, itemName);
        send(sellerId, TYPE_BID_PLACED, content, auctionId);
    }

    /**
     * Phiên sắp kết thúc (còn 5 phút) — gửi cho Seller.
     *
     * @param sellerId  ID seller
     * @param auctionId ID phiên
     * @param itemName  Tên sản phẩm
     */
    public void notifySellerAuctionEnding(String sellerId,
                                           String auctionId,
                                           String itemName) {
        String content = String.format(
                "⏰ Phiên \"%s\" của bạn còn dưới 5 phút!",
                itemName);
        send(sellerId, TYPE_AUCTION_ENDING, content, auctionId);
    }

    /**
     * Phiên của Seller vừa kết thúc.
     *
     * @param sellerId   ID seller
     * @param auctionId  ID phiên
     * @param itemName   Tên sản phẩm
     * @param winnerName Username người thắng (hoặc null nếu không có ai đặt giá)
     * @param finalPrice Giá cuối
     */
    public void notifySellerSessionEnded(String sellerId,
                                          String auctionId,
                                          String itemName,
                                          String winnerName,
                                          double finalPrice) {
        String content;
        if (winnerName == null || winnerName.isBlank()) {
            content = String.format(
                    "Phiên \"%s\" đã kết thúc. Không có ai đặt giá.", itemName);
        } else {
            content = String.format(
                    "🏁 Phiên \"%s\" đã kết thúc. Người thắng: %s với giá %,.0f ₫.",
                    itemName, winnerName, finalPrice);
        }
        send(sellerId, TYPE_SESSION_ENDED, content, auctionId);
    }

    // =========================================================================
    // Lấy notification cho client (REST-style request)
    // =========================================================================

    /**
     * Lấy danh sách notification của user dạng pipe-string.
     * Format: {@code NOTIFICATIONS|id;type;content;auctionId;isRead;createdAt|...}
     */
    public String buildGetResponse(String userId) {
        try {
            List<String[]> list = notificationDAO.getByUser(userId);
            if (list.isEmpty()) return "NOTIFICATIONS|trong";
            StringBuilder sb = new StringBuilder("NOTIFICATIONS");
            for (String[] row : list) {
                // row: [id, type, content, auctionId, isRead, createdAt]
                sb.append("|")
                  .append(row[0]).append(";")
                  .append(row[1]).append(";")
                  .append(row[2].replace("|", "｜").replace(";", "；")).append(";")
                  .append(row[3]).append(";")
                  .append(row[4]).append(";")
                  .append(row[5]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "LOI|Loi lay notifications: " + e.getMessage();
        }
    }

    /**
     * Đánh dấu tất cả notification của user là đã đọc.
     * Trả về "MARK_READ_SUCCESS" hoặc "LOI|..."
     */
    public String markAllRead(String userId) {
        try {
            notificationDAO.markAllRead(userId);
            return "MARK_READ_SUCCESS";
        } catch (Exception e) {
            return "LOI|Loi danh dau da doc: " + e.getMessage();
        }
    }

    // =========================================================================
    // Private helper
    // =========================================================================

    /**
     * Ghi DB + push socket realtime.
     */
    private void send(String userId, String type, String content, String auctionId) {
        // 1. Ghi vào DB
        try {
            notificationDAO.save(userId, type, content, auctionId);
        } catch (Exception e) {
            System.err.println("[NOTI] Lỗi lưu DB: " + e.getMessage());
        }

        // 2. Push realtime qua socket nếu user đang online
        // Format: NOTIFICATION|type|content|auctionId
        String socketMsg = "NOTIFICATION|" + type + "|"
                + content.replace("|", "｜") + "|"
                + (auctionId != null ? auctionId : "");
        server.sendToUser(userId, socketMsg);
    }
}
