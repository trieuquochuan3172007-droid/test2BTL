package com.auction.domain;

import com.auction.common.dto.BidResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test cho {@link AuctionSession}.
 *
 * <p><strong>Không phụ thuộc Database</strong> — sau khi refactor,
 * AuctionSession là pure domain object không có I/O.</p>
 */
@DisplayName("AuctionSession — Unit Tests")
class AuctionSessionTest {

    private static final String AUCTION_ID = "TEST-001";
    private static final String ITEM_ID    = "ITEM-001";
    private static final String ITEM_NAME  = "Laptop Test";
    private static final String SELLER_ID  = "SELLER-001";
    private static final double START_PRICE = 1_000_000.0;

    private AuctionSession session;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now();
        session = new AuctionSession(
                AUCTION_ID, ITEM_ID, ITEM_NAME, SELLER_ID,
                START_PRICE, now, now.plusHours(1));
    }

    // -------------------------------------------------------------------------
    // Từ chối bid khi phiên chưa RUNNING
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Từ chối bid khi phiên chưa được bắt đầu (PENDING)")
    void processBid_shouldRejectWhenStatusIsPending() {
        session.setStatus(AuctionStatus.PENDING);
        BidResult result = session.processBid("BIDDER-1", 2_000_000.0);

        assertFalse(result.success, "Phiên PENDING không được nhận bid");
        assertEquals(START_PRICE, session.getCurrentHighestBid(), "Giá không được thay đổi");
        assertNull(session.getWinnerID(), "Không có winner");
    }

    // -------------------------------------------------------------------------
    // Chấp nhận bid hợp lệ
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Chấp nhận bid hợp lệ và cập nhật giá + winner")
    void processBid_shouldAcceptValidBidAndUpdateState() {
        session.setStatus(AuctionStatus.RUNNING);
        double bidAmount = 1_500_000.0;

        BidResult result = session.processBid("BIDDER-1", bidAmount);

        assertTrue(result.success, "Bid hợp lệ phải được chấp nhận");
        assertEquals(bidAmount, session.getCurrentHighestBid(), "Giá phải được cập nhật");
        assertEquals("BIDDER-1", session.getWinnerID(), "Winner phải là người vừa bid");
        assertNotNull(result.transaction, "Phải có BidTransaction");
        assertEquals("BIDDER-1", result.transaction.getBidderID());
        assertEquals(bidAmount, result.transaction.getBidAmount());
    }

    // -------------------------------------------------------------------------
    // Từ chối bid thấp hơn hoặc bằng giá hiện tại
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Từ chối bid thấp hơn giá hiện tại")
    void processBid_shouldRejectBidLowerThanCurrentPrice() {
        session.setStatus(AuctionStatus.RUNNING);
        session.processBid("BIDDER-1", 2_000_000.0); // Bid đầu tiên

        BidResult result = session.processBid("BIDDER-2", 1_500_000.0); // Thấp hơn

        assertFalse(result.success, "Bid thấp hơn phải bị từ chối");
        assertEquals(2_000_000.0, session.getCurrentHighestBid(), "Giá không được thay đổi");
        assertEquals("BIDDER-1", session.getWinnerID(), "Winner không được thay đổi");
    }

    @Test
    @DisplayName("Từ chối bid bằng đúng giá hiện tại")
    void processBid_shouldRejectBidEqualToCurrentPrice() {
        session.setStatus(AuctionStatus.RUNNING);

        BidResult result = session.processBid("BIDDER-1", START_PRICE);

        assertFalse(result.success, "Bid bằng giá hiện tại phải bị từ chối");
    }

    // -------------------------------------------------------------------------
    // Thông tin hoàn tiền (refund) cho người dẫn đầu cũ
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Cung cấp thông tin hoàn tiền cho bidder cũ khi có bid mới")
    void processBid_shouldReturnRefundInfoForPreviousLeader() {
        session.setStatus(AuctionStatus.RUNNING);
        session.processBid("BIDDER-1", 2_000_000.0); // Bid đầu tiên

        BidResult result = session.processBid("BIDDER-2", 3_000_000.0); // Vượt mặt

        assertTrue(result.success);
        assertEquals("BIDDER-1", result.refundBidderID, "Phải hoàn tiền cho BIDDER-1");
        assertEquals(2_000_000.0, result.refundAmount, "Số tiền hoàn phải đúng");
    }

    @Test
    @DisplayName("Bid đầu tiên không có refundBidderID (chưa có người dẫn đầu cũ)")
    void processBid_firstBidShouldHaveNullRefund() {
        session.setStatus(AuctionStatus.RUNNING);

        BidResult result = session.processBid("BIDDER-1", 2_000_000.0);

        assertTrue(result.success);
        assertNull(result.refundBidderID, "Bid đầu tiên không cần hoàn tiền cho ai");
        assertEquals(0.0, result.refundAmount);
    }

    // -------------------------------------------------------------------------
    // Anti-sniping (gia hạn tự động)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Anti-sniping: gia hạn phiên khi có bid trong 30 giây cuối")
    void processBid_shouldExtendTimeWhenBidInLastThirtySeconds() {
        LocalDateTime now = LocalDateTime.now();
        session = new AuctionSession(AUCTION_ID, ITEM_ID, ITEM_NAME, SELLER_ID,
                START_PRICE, now, now.plusSeconds(20)); // Còn 20 giây
        session.setStatus(AuctionStatus.RUNNING);
        LocalDateTime originalEnd = session.getEndTime();

        BidResult result = session.processBid("BIDDER-1", 2_000_000.0);

        assertTrue(result.success);
        assertEquals(AuctionStatus.EXTENDED, session.getStatus(), "Trạng thái phải là EXTENDED");
        assertTrue(session.getEndTime().isAfter(originalEnd.plusSeconds(59)),
                "Thời gian phải được gia hạn thêm 60 giây");
    }

    @Test
    @DisplayName("Không gia hạn nếu bid xuất hiện khi còn nhiều thời gian")
    void processBid_shouldNotExtendTimeWhenPlentyOfTimeLeft() {
        session.setStatus(AuctionStatus.RUNNING); // Còn 1 giờ
        LocalDateTime originalEnd = session.getEndTime();

        session.processBid("BIDDER-1", 2_000_000.0);

        assertEquals(AuctionStatus.RUNNING, session.getStatus(), "Trạng thái không đổi");
        assertEquals(originalEnd, session.getEndTime(), "Thời gian kết thúc không đổi");
    }

    // -------------------------------------------------------------------------
    // Phiên hết giờ
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Từ chối bid sau khi phiên hết giờ và chuyển sang FAILED")
    void processBid_shouldRejectAndFinishWhenTimeExpired() {
        LocalDateTime now = LocalDateTime.now();
        session = new AuctionSession(AUCTION_ID, ITEM_ID, ITEM_NAME, SELLER_ID,
                START_PRICE, now.minusHours(2), now.minusSeconds(1)); // Đã hết giờ
        session.setStatus(AuctionStatus.RUNNING);

        BidResult result = session.processBid("BIDDER-1", 2_000_000.0);

        assertFalse(result.success, "Phiên hết giờ không được nhận bid");
        assertEquals(AuctionStatus.FAILED, session.getStatus(), "Trạng thái phải là FAILED");
    }

    // -------------------------------------------------------------------------
    // Lịch sử giao dịch
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("BidHistory lưu đúng số giao dịch thành công")
    void processBid_shouldRecordAllSuccessfulBids() {
        session.setStatus(AuctionStatus.RUNNING);

        session.processBid("BIDDER-1", 2_000_000.0);
        session.processBid("BIDDER-2", 3_000_000.0);
        session.processBid("BIDDER-1", 4_000_000.0);
        session.processBid("BIDDER-2", 2_500_000.0); // Thất bại — thấp hơn giá hiện tại

        assertEquals(3, session.getBidHistory().size(), "Phải có đúng 3 bid thành công");
        assertEquals(4_000_000.0, session.getCurrentHighestBid());
        assertEquals("BIDDER-1", session.getWinnerID());
    }
}
