package com.auction.common.dto;

import com.auction.domain.BidTransaction;

/**
 * DTO (Data Transfer Object) chứa kết quả của một lần đặt giá.
 * Tách rời logic nghiệp vụ (AuctionSession) với xử lý tầng dịch vụ (AuctionManager).
 *
 * <p>Pattern: Command / Value Object</p>
 */
public final class BidResult {

    public final boolean success;
    public final String message;
    public final BidTransaction transaction;   // null nếu thất bại

    /** ID người dẫn đầu cũ cần được hoàn tiền; null nếu không có */
    public final String refundBidderID;
    /** Số tiền cần hoàn lại cho người cũ */
    public final double refundAmount;

    // -------------------------------------------------------------------------
    // Constructor riêng tư — dùng factory methods bên dưới
    // -------------------------------------------------------------------------
    private BidResult(boolean success, String message,
                      BidTransaction transaction,
                      String refundBidderID, double refundAmount) {
        this.success        = success;
        this.message        = message;
        this.transaction    = transaction;
        this.refundBidderID = refundBidderID;
        this.refundAmount   = refundAmount;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Tạo BidResult thành công.
     *
     * @param tx              Giao dịch vừa được ghi nhận
     * @param refundBidderID  ID người dẫn đầu cũ (cần hoàn tiền), có thể null
     * @param refundAmount    Số tiền cần hoàn; 0 nếu không có người cũ
     */
    public static BidResult accepted(BidTransaction tx,
                                     String refundBidderID,
                                     double refundAmount) {
        return new BidResult(true, "Đặt giá thành công", tx, refundBidderID, refundAmount);
    }

    /**
     * Tạo BidResult thất bại.
     *
     * @param reason Lý do từ chối (để log và hiển thị cho người dùng)
     */
    public static BidResult rejected(String reason) {
        return new BidResult(false, reason, null, null, 0);
    }

    @Override
    public String toString() {
        return success
                ? "BidResult{SUCCESS, tx=" + transaction + ", refund=" + refundBidderID + "}"
                : "BidResult{REJECTED, reason=" + message + "}";
    }
}
