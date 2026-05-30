package com.auction.common.exception;

/**
 * Ném ra khi người dùng đặt giá không hợp lệ (thấp hơn giá hiện tại, âm, ...).
 */
public class InvalidBidException extends RuntimeException {
    private final double attempted;
    private final double current;

    public InvalidBidException(double attempted, double current) {
        super(String.format("Giá đặt %.0f không hợp lệ — phải cao hơn giá hiện tại %.0f",
                attempted, current));
        this.attempted = attempted;
        this.current   = current;
    }

    public InvalidBidException(String message) {
        super(message);
        this.attempted = 0;
        this.current   = 0;
    }

    public double getAttempted() { return attempted; }
    public double getCurrent()   { return current;   }
}
