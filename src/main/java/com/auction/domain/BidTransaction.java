package com.auction.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Đại diện cho một lần đặt giá thành công trong phiên đấu giá.
 *
 * <p>Là immutable value object — tất cả thuộc tính được thiết lập qua constructor
 * và không thể thay đổi sau đó (không có setter).</p>
 */
public final class BidTransaction implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss dd/MM/yyyy");

    private final String        auctionID;
    private final String        bidderID;
    private final double        bidAmount;
    private final LocalDateTime bidTime;

    public BidTransaction(String auctionID, String bidderID,
                           double bidAmount, LocalDateTime bidTime) {
        this.auctionID = auctionID;
        this.bidderID  = bidderID;
        this.bidAmount = bidAmount;
        this.bidTime   = (bidTime != null) ? bidTime : LocalDateTime.now();
    }

    public String        getAuctionID() { return auctionID; }
    public String        getBidderID()  { return bidderID;  }
    public double        getBidAmount() { return bidAmount; }
    public LocalDateTime getBidTime()   { return bidTime;   }

    @Override
    public String toString() {
        return String.format("BidTransaction{auction=%s, bidder=%s, amount=%,.0f, time=%s}",
                auctionID, bidderID, bidAmount, bidTime.format(DISPLAY_FMT));
    }
}
