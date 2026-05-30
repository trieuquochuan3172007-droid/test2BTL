package com.auction.common.models;

import java.io.Serializable;

/**
 * Quản lý ví tiền của Bidder với cơ chế tạm khóa (freeze/release).
 *
 * <p>Khi Bidder đặt giá, tiền được freeze (chuyển từ balance → frozenAmount).
 * Khi bị vượt mặt, tiền được release (hoàn lại từ frozenAmount → balance).
 * Khi thắng đấu giá, frozenAmount bị trừ hẳn (withdraw từ frozenAmount).</p>
 *
 * <pre>
 *   Deposit →  balance
 *   Bid      : balance ──freeze──► frozenAmount
 *   Bị thua  : frozenAmount ──release──► balance
 *   Thắng    : frozenAmount ──withdraw──► (trừ hẳn)
 * </pre>
 *
 * <p>Encapsulation: tất cả trường private, thay đổi chỉ qua các phương thức
 * có kiểm tra điều kiện.</p>
 */
public class Wallet implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Số dư khả dụng — có thể dùng để đặt giá hoặc rút tiền. */
    private double balance;

    /** Số tiền đang tạm khóa — chờ kết quả phiên đấu giá. */
    private double frozenAmount;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------
    public Wallet() {
        this.balance      = 0.0;
        this.frozenAmount = 0.0;
    }

    // -------------------------------------------------------------------------
    // Các thao tác cơ bản
    // -------------------------------------------------------------------------

    /**
     * Nạp tiền vào ví.
     *
     * @param amount Số tiền nạp (phải dương; âm hoặc zero bị bỏ qua)
     */
    public void deposit(double amount) {
        if (amount > 0) balance += amount;
    }

    /**
     * Rút tiền từ số dư khả dụng.
     *
     * @param amount Số tiền rút
     * @return true nếu đủ số dư; false nếu số tiền không hợp lệ hoặc vượt balance
     */
    public boolean withdraw(double amount) {
        if (amount <= 0 || balance < amount) return false;
        balance -= amount;
        return true;
    }

    // -------------------------------------------------------------------------
    // Cơ chế đặt cọc (Freeze / Release)
    // -------------------------------------------------------------------------

    /**
     * Tạm khóa tiền khi Bidder đặt giá — chuyển từ {@code balance} sang {@code frozenAmount}.
     *
     * @param amount Số tiền cần khóa
     * @return true nếu đủ số dư; false nếu không đủ hoặc amount không hợp lệ
     */
    public boolean freeze(double amount) {
        if (amount <= 0 || balance < amount) return false;
        balance       -= amount;
        frozenAmount  += amount;
        return true;
    }

    /**
     * Hoàn tiền khi Bidder bị vượt mặt — chuyển từ {@code frozenAmount} về {@code balance}.
     *
     * @param amount Số tiền hoàn lại (bị bỏ qua nếu vượt frozenAmount)
     */
    public void release(double amount) {
        if (amount <= 0 || frozenAmount < amount) return;
        frozenAmount -= amount;
        balance      += amount;
    }

    // -------------------------------------------------------------------------
    // Getters (không có setter thông thường — thay đổi chỉ qua các phương thức trên)
    // -------------------------------------------------------------------------

    /** Số dư khả dụng (không tính tiền đang bị tạm khóa). */
    public double getBalance()      { return balance;      }

    /** Số tiền đang bị tạm khóa chờ kết quả đấu giá. */
    public double getFrozenAmount() { return frozenAmount; }

    /** Tổng tiền trong ví (bao gồm cả tiền đang bị khóa). */
    public double getTotalBalance() { return balance + frozenAmount; }

    // -------------------------------------------------------------------------
    // Restore methods — CHỈ dùng cho khôi phục trạng thái từ Database (UserDAO)
    // KHÔNG gọi trực tiếp từ logic nghiệp vụ
    // -------------------------------------------------------------------------

    /**
     * Khôi phục balance trực tiếp từ giá trị đọc trong DB.
     * <strong>Chỉ gọi từ UserDAO.mapRowToUser — không dùng trong logic đấu giá.</strong>
     */
    public void restoreBalance(double balance) {
        this.balance = Math.max(0, balance);
    }

    /**
     * Khôi phục frozenAmount trực tiếp từ giá trị đọc trong DB.
     * <strong>Chỉ gọi từ UserDAO.mapRowToUser — không dùng trong logic đấu giá.</strong>
     */
    public void restoreFrozenAmount(double frozenAmount) {
        this.frozenAmount = Math.max(0, frozenAmount);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------
    @Override
    public String toString() {
        return String.format("Wallet{balance=%,.0f, frozen=%,.0f}", balance, frozenAmount);
    }
}
