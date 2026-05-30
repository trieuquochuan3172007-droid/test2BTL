package com.auction.domain;

import com.auction.common.dto.BidResult;
import com.auction.common.models.Bidder;
import com.auction.common.models.User;
import com.auction.server.dao.AuctionDAO;
import com.auction.server.dao.UserDAO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Singleton quản lý toàn bộ phiên đấu giá.
 *
 * <h3>Trách nhiệm</h3>
 * <ul>
 *   <li>Lưu trữ phiên trong {@link ConcurrentHashMap} an toàn đa luồng</li>
 *   <li>Scheduler 1 giây: tự động kích hoạt PENDING→RUNNING và đóng RUNNING→SUCCESS/FAILED</li>
 *   <li>Đăng ký anti-sniping callback cho mỗi phiên để Broadcast endTime mới</li>
 *   <li>Xử lý Wallet (freeze/release) khi đặt giá</li>
 * </ul>
 */
public class AuctionManager {

    // ─────────────────────────────────────────────────────────────────────────
    // Singleton (Double-Checked Locking)
    // ─────────────────────────────────────────────────────────────────────────
    private static volatile AuctionManager instance;

    private final Map<String, AuctionSession> sessions   = new ConcurrentHashMap<>();
    private final AuctionDAO                  auctionDAO = new AuctionDAO();
    private final ScheduledExecutorService    scheduler  =
            Executors.newScheduledThreadPool(1, r -> {
                Thread t = new Thread(r, "auction-scheduler");
                t.setDaemon(true);
                return t;
            });

    /**
     * Callback do Server đăng ký để Broadcast khi anti-sniping gia hạn endTime.
     * Tham số: (auctionID, endTimeISO)
     */
    private BiConsumer<String, String> broadcastExtensionCallback;

    /**
     * Callback do Server đăng ký để Broadcast khi phiên chuyển trạng thái.
     * Tham số: (auctionID, newStatus)
     */
    private BiConsumer<String, String> broadcastStatusCallback;

    private AuctionManager() {
        loadSessionsFromDatabase();
        startScheduler();
    }

    public static AuctionManager getInstance() {
        if (instance == null) {
            synchronized (AuctionManager.class) {
                if (instance == null) instance = new AuctionManager();
            }
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Đăng ký callbacks từ Server
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Server gọi hàm này một lần khi khởi động để đăng ký hàm Broadcast anti-sniping.
     *
     * @param callback nhận (auctionID, endTimeISOString)
     */
    public void setBroadcastExtensionCallback(BiConsumer<String, String> callback) {
        this.broadcastExtensionCallback = callback;
    }

    /**
     * Server gọi hàm này để đăng ký hàm Broadcast thay đổi trạng thái phiên.
     *
     * @param callback nhận (auctionID, newStatus)
     */
    public void setBroadcastStatusCallback(BiConsumer<String, String> callback) {
        this.broadcastStatusCallback = callback;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tạo / Thêm phiên
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo phiên đấu giá mới với startTime và endTime do Seller chọn.
     *
     * <p>Trạng thái ban đầu được xác định tự động bởi {@link AuctionSession}:
     * PENDING nếu startTime ở tương lai, RUNNING nếu bắt đầu ngay.</p>
     *
     * @param auctionID      Mã phiên duy nhất
     * @param itemID         Mã sản phẩm
     * @param itemName       Tên sản phẩm
     * @param sellerID       Mã người bán
     * @param startPrice     Giá khởi điểm
     * @param startTime      Thời điểm bắt đầu (do Seller chọn, có thể null → bắt đầu ngay)
     * @param endTime        Thời điểm kết thúc (do Seller chọn)
     * @return {@code true} nếu tạo thành công
     */
    public boolean createSession(String auctionID, String itemID, String itemName,
                                  String sellerID, double startPrice,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        if (auctionID == null || auctionID.isBlank()) return false;
        if (sessions.containsKey(auctionID)) return false;

        // Nếu startTime null → bắt đầu ngay lập tức
        LocalDateTime effectiveStart = (startTime != null) ? startTime : LocalDateTime.now();

        AuctionSession session = new AuctionSession(
                auctionID, itemID, itemName, sellerID, startPrice, effectiveStart, endTime);

        // Đăng ký callback anti-sniping
        registerAntiSnipingCallback(session);

        sessions.put(auctionID, session);
        saveSessionSilently(session);

        System.out.printf("[MANAGER] Phiên %s tạo thành công (trạng thái: %s, bắt đầu: %s)%n",
                auctionID, session.getStatus(), effectiveStart);
        return true;
    }

    /** Tương thích ngược: tạo phiên với durationMinutes (bắt đầu ngay). */
    public boolean createSession(String auctionID, String itemID, String itemName,
                                  String sellerID, double startPrice, int durationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        return createSession(auctionID, itemID, itemName, sellerID, startPrice,
                now, now.plusMinutes(Math.max(durationMinutes, 1)));
    }

    /** Tương thích ngược: tạo phiên đơn giản 60 phút. */
    public boolean createSession(String auctionID, String itemID,
                                  String sellerID, double startPrice) {
        return createSession(auctionID, itemID, itemID, sellerID, startPrice, 60);
    }

    public void addSession(AuctionSession session) {
        if (session != null && session.getAuctionID() != null) {
            registerAntiSnipingCallback(session);
            sessions.put(session.getAuctionID(), session);
        }
    }

    /** Tương thích ngược: kích hoạt phiên đấu giá thủ công (chuyển PENDING -> RUNNING). */
    public boolean startSession(String auctionID) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) return false;
        return session.activate();
    }

    /** Đăng ký anti-sniping callback cho một session. */
    private void registerAntiSnipingCallback(AuctionSession session) {
        session.setOnExtended(id -> {
            AuctionSession s = sessions.get(id);
            if (s == null) return;

            // 1. Persist endTime mới vào DB
            saveSessionSilently(s);

            // 2. Broadcast endTime mới về tất cả Client
            if (broadcastExtensionCallback != null) {
                String endTimeISO = s.getEndTime() != null ? s.getEndTime().toString() : "";
                broadcastExtensionCallback.accept(id, endTimeISO);
                System.out.printf("[MANAGER] Broadcast anti-sniping: phiên %s gia hạn đến %s%n",
                        id, endTimeISO);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scheduler — Trái tim của hệ thống thời gian thực
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Bộ quét ngầm chạy mỗi giây, xử lý 2 sự kiện thời gian:
     *
     * <ol>
     *   <li><strong>PENDING → RUNNING</strong>: khi đến startTime</li>
     *   <li><strong>RUNNING/EXTENDED → SUCCESS/FAILED</strong>: khi hết endTime</li>
     * </ol>
     */
    private void startScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            for (AuctionSession session : sessions.values()) {
                try {
                    tickSession(session);
                } catch (Exception e) {
                    System.err.println("[SCHEDULER] Lỗi xử lý phiên "
                            + session.getAuctionID() + ": " + e.getMessage());
                }
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * Xử lý một phiên trong mỗi tick của scheduler.
     */
    private void tickSession(AuctionSession session) {
        AuctionStatus st = session.getStatus();

        // ─── Sự kiện 1: Kích hoạt phiên PENDING → RUNNING ───
        if (st == AuctionStatus.PENDING && session.isReadyToStart()) {
            boolean activated = session.activate();
            if (activated) {
                saveSessionSilently(session);
                if (broadcastStatusCallback != null) {
                    broadcastStatusCallback.accept(session.getAuctionID(), "RUNNING");
                }
                System.out.printf("[SCHEDULER] ▶ Phiên %s: PENDING → RUNNING%n",
                        session.getAuctionID());
            }
            return;
        }

        // ─── Sự kiện 2: Đóng phiên hết giờ RUNNING/EXTENDED → SUCCESS/FAILED ───
        if (session.isExpired()) {
            boolean closed = session.closeByScheduler();
            if (closed) {
                AuctionStatus result = session.getStatus(); // SUCCESS hoặc FAILED
                saveSessionSilently(session);

                // Broadcast kết thúc
                if (broadcastStatusCallback != null) {
                    broadcastStatusCallback.accept(session.getAuctionID(), result.name());
                }
                System.out.printf("[SCHEDULER] ■ Phiên %s: HẾT GIỜ → %s (winner=%s)%n",
                        session.getAuctionID(), result, session.getWinnerID());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Đặt giá — xử lý Wallet
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xử lý một lần đặt giá:
     * <ol>
     *   <li>Ủy quyền logic nghiệp vụ cho {@link AuctionSession#processBid}</li>
     *   <li>Freeze tiền người mới, release tiền người cũ</li>
     *   <li>Lưu giao dịch và trạng thái phiên vào DB</li>
     * </ol>
     */
    public boolean placeBid(String auctionID, String bidderID, double bidAmount) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) {
            System.err.println("[MANAGER] Không tìm thấy phiên: " + auctionID);
            return false;
        }

        BidResult result = session.processBid(bidderID, bidAmount);
        if (!result.success) {
            System.out.println("[MANAGER] Đặt giá bị từ chối: " + result.message);
            return false;
        }

        processWalletOperations(result, bidAmount);

        try {
            auctionDAO.saveBidTransaction(result.transaction);
            saveSessionSilently(session);
        } catch (Exception e) {
            System.err.println("[MANAGER] Lỗi lưu giao dịch: " + e.getMessage());
        }

        return true;
    }

    private void processWalletOperations(BidResult result, double bidAmount) {
        try {
            UserDAO userDAO = new UserDAO();
            String newBidderID = result.transaction.getBidderID();
            User newUser = userDAO.findById(newBidderID);
            if (newUser instanceof Bidder newBidder) {
                boolean frozen = newBidder.getWallet().freeze(bidAmount);
                if (!frozen) {
                    System.out.printf("[WALLET] ⚠ %s không đủ tiền để freeze %.0f%n",
                            newBidderID, bidAmount);
                } else {
                    userDAO.saveUser(newBidder);
                }
            }

            String oldBidderID = result.refundBidderID;
            if (oldBidderID != null && !oldBidderID.isBlank()) {
                User oldUser = userDAO.findById(oldBidderID);
                if (oldUser instanceof Bidder oldBidder) {
                    oldBidder.getWallet().release(result.refundAmount);
                    userDAO.saveUser(oldBidder);
                    System.out.printf("[WALLET] Hoàn %.0f về cho %s%n",
                            result.refundAmount, oldBidderID);
                }
            }
        } catch (Exception e) {
            System.err.println("[WALLET] Lỗi xử lý ví: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Đóng phiên thủ công (Admin/Seller)
    // ─────────────────────────────────────────────────────────────────────────

    /** Đóng phiên thủ công — trả SUCCESS/CANCELED tuỳ có bid hay không. */
    public boolean closeSession(String auctionID) {
        AuctionSession session = sessions.get(auctionID);
        if (session == null) return false;

        boolean ok = session.closeManually();
        if (ok) {
            saveSessionSilently(session);
            if (broadcastStatusCallback != null) {
                broadcastStatusCallback.accept(auctionID, session.getStatus().name());
            }
        }
        return ok;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Truy vấn
    // ─────────────────────────────────────────────────────────────────────────
    public AuctionSession getSession(String auctionID) { return sessions.get(auctionID); }

    public List<AuctionSession> getAllSessions() {
        return Collections.unmodifiableList(new ArrayList<>(sessions.values()));
    }

    public void removeSession(String auctionID) { sessions.remove(auctionID); }

    // ─────────────────────────────────────────────────────────────────────────
    // Lưu tất cả phiên (shutdown hook)
    // ─────────────────────────────────────────────────────────────────────────
    public void persistAllSessions() {
        int count = 0;
        for (AuctionSession session : sessions.values()) {
            saveSessionSilently(session);
            count++;
        }
        System.out.println("[MANAGER] ✓ Đã lưu " + count + " phiên vào database.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DB Helpers
    // ─────────────────────────────────────────────────────────────────────────
    private void saveSessionSilently(AuctionSession session) {
        try {
            auctionDAO.saveSession(session);
        } catch (Exception e) {
            System.err.println("[MANAGER] ⚠ Lỗi lưu phiên " + session.getAuctionID()
                    + ": " + e.getMessage());
        }
    }

    private void loadSessionsFromDatabase() {
        try {
            List<AuctionSession> loaded = auctionDAO.getAllSessions();
            for (AuctionSession s : loaded) {
                // Chỉ nạp lại các phiên chưa kết thúc hoàn toàn
                if (!s.getStatus().isTerminal()) {
                    registerAntiSnipingCallback(s);
                    sessions.put(s.getAuctionID(), s);
                    System.out.println("[MANAGER] Đã nạp phiên từ DB: "
                            + s.getAuctionID() + " (" + s.getStatus() + ")");
                }
            }
        } catch (Exception e) {
            System.err.println("[MANAGER] ⚠ Lỗi nạp phiên từ DB: " + e.getMessage());
        }
    }
}
