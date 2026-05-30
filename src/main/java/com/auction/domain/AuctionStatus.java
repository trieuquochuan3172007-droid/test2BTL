package com.auction.domain;

/**
 * Trạng thái vòng đời của một phiên đấu giá.
 *
 * <p>Luồng trạng thái hợp lệ:
 * <pre>
 *   PENDING → RUNNING ⇄ EXTENDED → SUCCESS (có bid)
 *                                 ↘ FAILED  (không có bid)
 *                                 ↘ CANCELED (seller hủy thủ công)
 * </pre>
 * </p>
 *
 * <p><strong>PENDING</strong>: Phiên đã được tạo nhưng chưa đến giờ bắt đầu.
 * Server tự động chuyển sang RUNNING khi đến startTime.</p>
 */
public enum AuctionStatus {

    /** Phiên đã tạo — chờ đến giờ bắt đầu (startTime ở tương lai). */
    PENDING,

    /** Phiên đang hoạt động — người dùng có thể đặt giá. */
    RUNNING,

    /**
     * Phiên được gia hạn tự động (Anti-sniping):
     * có bid xuất hiện trong 30 giây cuối, endTime được cộng thêm 30 giây.
     */
    EXTENDED,

    /** Phiên kết thúc thành công — có ít nhất một lượt đặt giá hợp lệ. */
    SUCCESS,

    /** Phiên kết thúc thất bại — không có ai đặt giá trước khi hết giờ. */
    FAILED,

    /** Phiên bị hủy thủ công (Admin/Seller can thiệp). */
    CANCELED;

    // -------------------------------------------------------------------------
    // Các tiện ích kiểm tra trạng thái
    // -------------------------------------------------------------------------

    /** Phiên đang nhận bid không? (RUNNING hoặc EXTENDED). */
    public boolean isAcceptingBids() {
        return this == RUNNING || this == EXTENDED;
    }

    /** Phiên đã kết thúc hoàn toàn (không thể đặt giá)? */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED;
    }

    /** Phiên chưa bắt đầu? */
    public boolean isPending() {
        return this == PENDING;
    }

    /**
     * Ánh xạ từ chuỗi DB (tương thích ngược với OPEN/FINISHED cũ).
     * Dùng khi đọc từ MySQL.
     */
    public static AuctionStatus fromDbString(String raw) {
        if (raw == null) return FAILED;
        return switch (raw.toUpperCase().trim()) {
            case "PENDING"                  -> PENDING;
            case "RUNNING", "OPEN"          -> RUNNING;
            case "EXTENDED"                 -> EXTENDED;
            case "SUCCESS", "PAID"          -> SUCCESS;
            case "FAILED", "CANCELED", "FINISHED" -> FAILED;
            default -> {
                try { yield AuctionStatus.valueOf(raw.toUpperCase()); }
                catch (IllegalArgumentException e) { yield FAILED; }
            }
        };
    }
}
