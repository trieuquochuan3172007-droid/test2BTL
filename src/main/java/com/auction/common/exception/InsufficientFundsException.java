package com.auction.common.exception;

/**
 * Ném ra khi Bidder không đủ số dư để đặt cọc.
 */
public class InsufficientFundsException extends RuntimeException {
    private final String bidderId;
    private final double required;
    private final double available;

    public InsufficientFundsException(String bidderId, double required, double available) {
        super(String.format("Bidder %s không đủ số dư: cần %.0f VNĐ, hiện có %.0f VNĐ",
                bidderId, required, available));
        this.bidderId  = bidderId;
        this.required  = required;
        this.available = available;
    }

    public String getBidderId()  { return bidderId;  }
    public double getRequired()  { return required;  }
    public double getAvailable() { return available; }
}
