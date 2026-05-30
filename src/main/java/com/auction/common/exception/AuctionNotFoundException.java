package com.auction.common.exception;

/**
 * Ném ra khi không tìm thấy phiên đấu giá theo ID.
 */
public class AuctionNotFoundException extends RuntimeException {
    private final String auctionId;

    public AuctionNotFoundException(String auctionId) {
        super("Không tìm thấy phiên đấu giá: " + auctionId);
        this.auctionId = auctionId;
    }

    public String getAuctionId() { return auctionId; }
}
