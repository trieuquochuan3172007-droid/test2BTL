package com.auction.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link AuctionManager}.
 *
 * <p>Các test này hoạt động không cần Database — wallet và DAO
 * được xử lý best-effort (bắt ngoại lệ bên trong AuctionManager).</p>
 */
@DisplayName("AuctionManager — Unit Tests")
class AuctionManagerTest {

    private AuctionManager manager;

    @BeforeEach
    void setUp() {
        manager = AuctionManager.getInstance();
    }

    // -------------------------------------------------------------------------
    // Tạo & Quản lý phiên
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Tạo phiên mới, start, bid, và close — luồng hoàn chỉnh")
    void fullFlow_createStartBidClose_shouldFollowExpectedStates() {
        String auctionId = "FLOW-" + System.nanoTime();

        // Tạo và start phiên
        LocalDateTime now = LocalDateTime.now();
        assertTrue(manager.createSession(auctionId, "ITEM-1", "SESSION-ITEM", "SELLER-1", 100.0, now.plusHours(1), now.plusHours(2)),
                "Tạo phiên mới phải thành công");
        assertTrue(manager.startSession(auctionId),
                "Start phiên PENDING phải thành công");

        // Đặt giá (wallet không có trong test → bỏ qua, bid vẫn được ghi nhận)
        boolean bidOk = manager.placeBid(auctionId, "BIDDER-1", 150.0);
        assertTrue(bidOk, "Bid hợp lệ phải được chấp nhận");

        // Đóng phiên
        assertTrue(manager.closeSession(auctionId), "Close phiên RUNNING phải thành công");

        // Kiểm tra trạng thái cuối
        AuctionSession session = manager.getSession(auctionId);
        assertNotNull(session);
        assertEquals(150.0,                   session.getCurrentHighestBid(), "Giá phải là 150.0");
        assertEquals("BIDDER-1",             session.getWinnerID(),          "Winner phải là BIDDER-1");
        assertEquals(AuctionStatus.SUCCESS, session.getStatus(),            "Trạng thái phải SUCCESS");
    }

    @Test
    @DisplayName("Từ chối tạo phiên với ID đã tồn tại")
    void createSession_shouldRejectDuplicateAuctionId() {
        String auctionId = "DUP-" + System.nanoTime();

        assertTrue(manager.createSession(auctionId, "ITEM-A", "SELLER-A", 200.0));
        assertFalse(manager.createSession(auctionId, "ITEM-B", "SELLER-B", 300.0),
                "Không được tạo phiên với ID đã tồn tại");
    }

    @Test
    @DisplayName("Từ chối tạo phiên với ID null hoặc rỗng")
    void createSession_shouldRejectBlankAuctionId() {
        assertFalse(manager.createSession(null,  "ITEM-X", "SELLER-X", 100.0), "null ID phải bị từ chối");
        assertFalse(manager.createSession("",    "ITEM-X", "SELLER-X", 100.0), "empty ID phải bị từ chối");
        assertFalse(manager.createSession("   ", "ITEM-X", "SELLER-X", 100.0), "blank ID phải bị từ chối");
    }

    // -------------------------------------------------------------------------
    // Đặt giá qua AuctionManager
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Từ chối bid vào phiên không tồn tại")
    void placeBid_shouldReturnFalseForUnknownAuction() {
        boolean result = manager.placeBid("NONEXISTENT-AUCTION", "BIDDER-X", 999.0);
        assertFalse(result, "Bid vào phiên không tồn tại phải bị từ chối");
    }

    @Test
    @DisplayName("Từ chối bid vào phiên chưa RUNNING (trạng thái OPEN)")
    void placeBid_shouldRejectWhenSessionNotRunning() {
        String auctionId = "NOTRUN-" + System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        manager.createSession(auctionId, "ITEM-NR", "ITEM-NR", "SELLER-NR", 100.0, now.plusHours(1), now.plusHours(2));
        // Không gọi startSession → trạng thái vẫn là PENDING

        assertFalse(manager.placeBid(auctionId, "BIDDER-NR", 150.0),
                "Bid vào phiên PENDING phải bị từ chối");
    }

    @Test
    @DisplayName("Bid thứ hai phải thắng bid thứ nhất nếu giá cao hơn")
    void placeBid_secondHigherBidShouldWin() {
        String auctionId = "BID2-" + System.nanoTime();
        manager.createSession(auctionId, "ITEM-B2", "ITEM-B2", "SELLER-B2", 100.0, 60);
        manager.startSession(auctionId);

        manager.placeBid(auctionId, "BIDDER-A", 200.0);
        manager.placeBid(auctionId, "BIDDER-B", 350.0); // Vượt mặt

        AuctionSession session = manager.getSession(auctionId);
        assertEquals(350.0,     session.getCurrentHighestBid());
        assertEquals("BIDDER-B", session.getWinnerID());
    }

    @Test
    @DisplayName("Từ chối bid thấp hơn giá hiện tại trong phiên RUNNING")
    void placeBid_lowerBidShouldBeRejected() {
        String auctionId = "LOW-" + System.nanoTime();
        manager.createSession(auctionId, "ITEM-LOW", "ITEM-LOW", "SELLER-LOW", 100.0, 60);
        manager.startSession(auctionId);

        manager.placeBid(auctionId, "BIDDER-X", 500.0); // Bid đầu tiên

        boolean rejected = manager.placeBid(auctionId, "BIDDER-Y", 300.0); // Thấp hơn

        assertFalse(rejected, "Bid thấp hơn phải bị từ chối");
        assertEquals("BIDDER-X", manager.getSession(auctionId).getWinnerID());
    }

    // -------------------------------------------------------------------------
    // Start / Close session
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Không thể start phiên đã RUNNING")
    void startSession_shouldRejectAlreadyRunningSession() {
        String auctionId = "START-RUN-" + System.nanoTime();
        LocalDateTime now = LocalDateTime.now();
        manager.createSession(auctionId, "ITEM-SR", "ITEM-SR", "SELLER-SR", 100.0, now.plusHours(1), now.plusHours(2));
        assertTrue(manager.startSession(auctionId), "Lần 1 start PENDING thành công");
        assertFalse(manager.startSession(auctionId), "Không thể start phiên đang RUNNING");
    }

    @Test
    @DisplayName("Không thể close phiên không tồn tại")
    void closeSession_shouldReturnFalseForUnknownSession() {
        assertFalse(manager.closeSession("GHOST-SESSION-XYZ"),
                "Close phiên không tồn tại phải trả false");
    }

    @Test
    @DisplayName("Không thể close phiên đã FINISHED")
    void closeSession_shouldRejectFinishedSession() {
        String auctionId = "DONE-" + System.nanoTime();
        manager.createSession(auctionId, "ITEM-DN", "SELLER-DN", 100.0);
        manager.startSession(auctionId);
        manager.closeSession(auctionId); // Đóng lần 1

        assertFalse(manager.closeSession(auctionId), "Close phiên đã FINISHED phải trả false");
    }
}
