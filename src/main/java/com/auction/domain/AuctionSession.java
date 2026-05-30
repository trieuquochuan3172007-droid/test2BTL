package com.auction.domain;

import com.auction.common.dto.BidResult;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Đại diện cho một phiên đấu giá — <strong>thuần nghiệp vụ, không phụ thuộc DB</strong>.
 *
 * <h3>Vòng đời trạng thái</h3>
 * <pre>
 *   PENDING ──(đến startTime)──► RUNNING ⇄ EXTENDED ──(hết giờ)──► SUCCESS (có bid)
 *                                                                  ↘ FAILED  (không bid)
 * </pre>
 *
 * <h3>Thread-safety</h3>
 * <p>Tất cả thao tác thay đổi {@code endTime} và {@code status} được bảo vệ bởi
 * {@link ReentrantLock}, đảm bảo an toàn khi nhiều Bidder cùng đặt giá ở giây cuối.</p>
 *
 * <h3>Anti-sniping (Chống bắn tỉa phút cuối)</h3>
 * <p>Nếu một bid hợp lệ xuất hiện trong {@value #ANTI_SNIPING_THRESHOLD_SECONDS} giây cuối,
 * {@code endTime} tự động được cộng thêm {@value #EXTENSION_SECONDS} giây.
 * Sau khi gia hạn, {@code onExtended} callback được gọi để Server có thể Broadcast
 * thời gian mới về cho mọi Client.</p>
 */
public class AuctionSession {

    // ─────────────────────────────────────────────────────────────────────────
    // Hằng số Anti-sniping
    // ─────────────────────────────────────────────────────────────────────────

    /** Ngưỡng giây cuối mà nếu có bid xuất hiện thì kích hoạt gia hạn. */
    public static final long ANTI_SNIPING_THRESHOLD_SECONDS = 30;

    /** Số giây được cộng thêm vào endTime mỗi lần anti-sniping kích hoạt. */
    public static final long EXTENSION_SECONDS = 60;

    // ─────────────────────────────────────────────────────────────────────────
    // Lock — bảo vệ endTime & status khỏi Race Condition đa luồng
    // ─────────────────────────────────────────────────────────────────────────
    private final ReentrantLock timeLock = new ReentrantLock();

    // ─────────────────────────────────────────────────────────────────────────
    // Thuộc tính phiên đấu giá
    // ─────────────────────────────────────────────────────────────────────────
    private String        auctionID;
    private String        itemID;
    private String        itemName;
    private String        sellerID;
    private double        currentHighestBid;
    private String        currentHighestBidderID;
    private String        winnerID;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AuctionStatus status;
    private List<BidTransaction> bidHistory;

    /**
     * Callback được gọi bởi {@link #checkAndExtendTime()} sau khi gia hạn thành công.
     * Server đăng ký lambda vào đây để Broadcast endTime mới về cho mọi Client.
     *
     * <p>Nhận tham số: {@code auctionID} của phiên vừa được gia hạn.</p>
     */
    private Consumer<String> onExtended;

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructor đầy đủ: Tự động xác định trạng thái ban đầu.
     *
     * <ul>
     *   <li>Nếu {@code startTime} ở <strong>tương lai</strong> → trạng thái {@code PENDING}.</li>
     *   <li>Nếu {@code startTime} đã qua (hoặc ngay lập tức) → trạng thái {@code RUNNING}.</li>
     * </ul>
     *
     * @param auctionID       Mã phiên duy nhất
     * @param itemID          Mã sản phẩm
     * @param itemName        Tên hiển thị sản phẩm
     * @param sellerID        Mã người bán
     * @param startPrice      Giá khởi điểm
     * @param startTime       Thời điểm bắt đầu (do Seller chọn)
     * @param endTime         Thời điểm kết thúc (do Seller chọn)
     */
    public AuctionSession(String auctionID, String itemID, String itemName, String sellerID,
                          double startPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this.auctionID              = auctionID;
        this.itemID                 = itemID;
        this.itemName               = (itemName != null && !itemName.isBlank()) ? itemName : itemID;
        this.sellerID               = sellerID;
        this.currentHighestBid      = startPrice;
        this.currentHighestBidderID = null;
        this.winnerID               = null;
        this.startTime              = startTime;
        this.endTime                = endTime;
        this.bidHistory             = new ArrayList<>();

        // ─── Xác định trạng thái khởi tạo thông minh ───
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && startTime.isAfter(now)) {
            this.status = AuctionStatus.PENDING;   // Chờ đến giờ bắt đầu
        } else {
            this.status = AuctionStatus.RUNNING;   // Bắt đầu ngay lập tức
        }
    }

    /** Constructor tương thích ngược (itemName = itemID). */
    public AuctionSession(String auctionID, String itemID, String sellerID,
                          double startPrice, LocalDateTime startTime, LocalDateTime endTime) {
        this(auctionID, itemID, itemID, sellerID, startPrice, startTime, endTime);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Nghiệp vụ đặt giá — Thread-safe
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý một lần đặt giá. <strong>Thread-safe</strong> hoàn toàn:
     *
     * <ol>
     *   <li>Toàn bộ khối logic được khóa bởi {@code synchronized(this)}</li>
     *   <li>Kiểm tra endTime (đọc) và cập nhật endTime (viết) đều nằm trong lock</li>
     * </ol>
     *
     * @param bidderID  ID người đặt giá
     * @param bidAmount Số tiền đặt
     * @return {@link BidResult} thành công hoặc thất bại kèm lý do
     */
    public BidResult processBid(String bidderID, double bidAmount) {
        timeLock.lock();
        try {
            if (bidHistory == null) bidHistory = new ArrayList<>();

            // 1. Kiểm tra trạng thái: phải RUNNING hoặc EXTENDED
            if (!status.isAcceptingBids()) {
                String hint = status.isPending()
                        ? "Phiên chưa đến giờ bắt đầu (PENDING)"
                        : "Phiên không còn nhận bid (trạng thái: " + status + ")";
                return BidResult.rejected(hint);
            }

            // 2. Kiểm tra thời gian hiện tại
            LocalDateTime now = LocalDateTime.now();
            if (endTime != null && now.isAfter(endTime)) {
                status = AuctionStatus.FAILED; // Hết giờ, không ai kịp vào
                return BidResult.rejected("Phiên đấu giá đã hết thời gian");
            }

            // 3. Kiểm tra giá hợp lệ
            if (bidAmount <= 0) {
                return BidResult.rejected("Số tiền đặt giá phải lớn hơn 0");
            }
            if (bidAmount <= currentHighestBid) {
                return BidResult.rejected(String.format(
                        "Giá đặt %.0f phải cao hơn giá cao nhất hiện tại %.0f",
                        bidAmount, currentHighestBid));
            }

            // 4. Ghi nhớ bidder cũ để hoàn tiền
            String previousBidderID  = this.currentHighestBidderID;
            double previousBidAmount = (previousBidderID != null) ? this.currentHighestBid : 0.0;

            // 5. Cập nhật trạng thái phiên
            this.currentHighestBid      = bidAmount;
            this.currentHighestBidderID = bidderID;
            this.winnerID               = bidderID;

            // 6. Ghi lịch sử
            BidTransaction transaction = new BidTransaction(auctionID, bidderID, bidAmount, now);
            bidHistory.add(transaction);

            // 7. Kiểm tra và kích hoạt Anti-sniping (trong cùng lock, endTime được ghi an toàn)
            boolean extended = checkAndExtendTime(now);

            System.out.printf("[PHIÊN %s] %s đặt %.0f %s%n",
                    auctionID, bidderID, bidAmount, extended ? "→ Anti-sniping kích hoạt!" : "");

            return BidResult.accepted(transaction, previousBidderID, previousBidAmount);

        } finally {
            timeLock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Anti-sniping — Được gọi BÊN TRONG timeLock
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra và gia hạn thời gian nếu bid xuất hiện trong 30 giây cuối.
     *
     * <p><strong>Quan trọng:</strong> Phương thức này phải được gọi bên trong
     * {@code timeLock} để tránh Race Condition khi nhiều thread cùng đọc/ghi
     * {@code endTime} đồng thời.</p>
     *
     * @param now Thời điểm hiện tại (tránh gọi LocalDateTime.now() nhiều lần)
     * @return {@code true} nếu đã gia hạn thành công
     */
    private boolean checkAndExtendTime(LocalDateTime now) {
        if (endTime == null || status.isTerminal()) return false;

        long secondsLeft = Duration.between(now, endTime).getSeconds();
        if (secondsLeft > 0 && secondsLeft <= ANTI_SNIPING_THRESHOLD_SECONDS) {
            // ─── Gia hạn endTime ───
            endTime = endTime.plusSeconds(EXTENSION_SECONDS);
            status  = AuctionStatus.EXTENDED;

            System.out.printf("[PHIÊN %s] ⏰ Anti-sniping: còn %ds → gia hạn thêm %ds (endTime mới: %s)%n",
                    auctionID, secondsLeft, EXTENSION_SECONDS, endTime);

            // ─── Gọi callback để Server Broadcast endTime mới ───
            if (onExtended != null) {
                // Chạy trên thread riêng để không block lock quá lâu
                String id = this.auctionID;
                new Thread(() -> onExtended.accept(id), "anti-snipe-broadcast-" + id).start();
            }
            return true;
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chuyển trạng thái do Server Scheduler gọi — Thread-safe
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kích hoạt phiên: PENDING → RUNNING.
     * Được gọi bởi {@code AuctionManager} scheduler khi đến startTime.
     *
     * @return {@code true} nếu chuyển trạng thái thành công
     */
    public boolean activate() {
        timeLock.lock();
        try {
            if (status != AuctionStatus.PENDING) return false;
            status = AuctionStatus.RUNNING;
            System.out.printf("[PHIÊN %s] ▶ Đã mở — bắt đầu nhận bid!%n", auctionID);
            return true;
        } finally {
            timeLock.unlock();
        }
    }

    /**
     * Đóng phiên khi hết giờ. Kết quả: SUCCESS nếu có bid, FAILED nếu không.
     * Được gọi bởi {@code AuctionManager} scheduler.
     *
     * @return {@code true} nếu đóng thành công (phiên đang RUNNING/EXTENDED)
     */
    public boolean closeByScheduler() {
        timeLock.lock();
        try {
            if (!status.isAcceptingBids()) return false;

            boolean hasBid = (winnerID != null && !winnerID.isBlank());
            status = hasBid ? AuctionStatus.SUCCESS : AuctionStatus.FAILED;

            System.out.printf("[PHIÊN %s] ■ Kết thúc: %s (winner=%s)%n",
                    auctionID, status, winnerID != null ? winnerID : "none");
            return true;
        } finally {
            timeLock.unlock();
        }
    }

    /**
     * Đóng phiên thủ công bởi Admin/Seller.
     * Luôn thành công bất kể trạng thái (trừ đã terminal).
     *
     * @return {@code true} nếu đóng được
     */
    public boolean closeManually() {
        timeLock.lock();
        try {
            if (status.isTerminal()) return false;
            boolean hasBid = (winnerID != null && !winnerID.isBlank());
            status = hasBid ? AuctionStatus.SUCCESS : AuctionStatus.CANCELED;
            return true;
        } finally {
            timeLock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Kiểm tra thời gian — đọc an toàn
    // ─────────────────────────────────────────────────────────────────────────

    /** Trả {@code true} nếu đã đến giờ kích hoạt (startTime đã qua). */
    public boolean isReadyToStart() {
        return status == AuctionStatus.PENDING
                && startTime != null
                && !LocalDateTime.now().isBefore(startTime);
    }

    /** Trả {@code true} nếu đã hết giờ (endTime đã qua, phiên còn đang nhận bid). */
    public boolean isExpired() {
        timeLock.lock();
        try {
            return status.isAcceptingBids()
                    && endTime != null
                    && LocalDateTime.now().isAfter(endTime);
        } finally {
            timeLock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getters & Setters
    // ─────────────────────────────────────────────────────────────────────────
    public String getAuctionID()   { return auctionID; }
    public void   setAuctionID(String v) { this.auctionID = v; }

    public String getItemID()   { return itemID; }
    public void   setItemID(String v) { this.itemID = v; }

    public String getItemName() { return itemName; }
    public void   setItemName(String v) { this.itemName = v; }

    /** Ưu tiên trả tên sản phẩm; nếu không có tên thì trả ID. */
    public String getDisplayItem() {
        return (itemName != null && !itemName.isBlank()) ? itemName : itemID;
    }

    /** Đặt tên hiển thị (dùng khi seller cập nhật). */
    public void setDisplayItem(String name) {
        this.itemName = (name != null && !name.isBlank()) ? name : itemID;
    }

    public String getSellerID()   { return sellerID; }
    public void   setSellerID(String v) { this.sellerID = v; }

    public double getCurrentHighestBid() { return currentHighestBid; }
    public void   setCurrentHighestBid(double v) { this.currentHighestBid = v; }

    public String getCurrentHighestBidderID() { return currentHighestBidderID; }
    public void   setCurrentHighestBidderID(String v) { this.currentHighestBidderID = v; }

    public String getWinnerID()   { return winnerID; }
    public void   setWinnerID(String v) { this.winnerID = v; }

    public LocalDateTime getStartTime() { return startTime; }
    public void          setStartTime(LocalDateTime v) { this.startTime = v; }

    /**
     * Đọc endTime an toàn từ bên ngoài lock (snapshot).
     * Dùng để gửi về Client qua network — thread-safe vì LocalDateTime là immutable.
     */
    public LocalDateTime getEndTime() {
        timeLock.lock();
        try { return endTime; }
        finally { timeLock.unlock(); }
    }

    /** Ghi endTime trực tiếp (chỉ dùng khi restore từ DB hoặc Admin override). */
    public void setEndTime(LocalDateTime v) {
        timeLock.lock();
        try { this.endTime = v; }
        finally { timeLock.unlock(); }
    }

    public AuctionStatus getStatus() {
        timeLock.lock();
        try { return status; }
        finally { timeLock.unlock(); }
    }

    public void setStatus(AuctionStatus v) {
        timeLock.lock();
        try { this.status = v; }
        finally { timeLock.unlock(); }
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory != null ? bidHistory : List.of());
    }

    public int getParticipantCount() {
        if (bidHistory == null) return 0;
        return (int) bidHistory.stream()
                .map(BidTransaction::getBidderID)
                .distinct()
                .count();
    }

    /**
     * Đăng ký callback để Server Broadcast khi anti-sniping gia hạn endTime.
     *
     * <p>Ví dụ sử dụng trong {@code AuctionManager}:</p>
     * <pre>{@code
     *   session.setOnExtended(id -> server.broadcastExtension(id, session.getEndTime()));
     * }</pre>
     *
     * @param callback nhận {@code auctionID} làm tham số
     */
    public void setOnExtended(Consumer<String> callback) {
        this.onExtended = callback;
    }
}
