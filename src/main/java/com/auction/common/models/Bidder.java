package com.auction.common.models;

/**
 * Người tham gia đấu giá (Bidder).
 *
 * <p>Bidder sở hữu một {@link Wallet} để quản lý tài chính:
 * nạp tiền, đặt cọc (freeze) khi bid, nhận lại (release) khi bị vượt mặt.</p>
 *
 * <p>Kế thừa {@link User} — có đầy đủ thông tin tài khoản và được
 * phân quyền tham gia đấu giá.</p>
 */
public class Bidder extends User {

    private final Wallet wallet;

    /**
     * Tạo Bidder mới với số dư ban đầu.
     *
     * @param id        Mã định danh duy nhất
     * @param username  Tên đăng nhập
     * @param password  BCrypt hash (không truyền plaintext)
     * @param fullName  Tên hiển thị
     * @param email     Địa chỉ email
     * @param balance   Số dư ban đầu (VNĐ); nếu ≤ 0 thì bắt đầu với ví trống
     */
    public Bidder(String id, String username, String password,
                  String fullName, String email, double balance) {
        super(id, username, password, fullName, email);
        this.wallet = new Wallet();
        if (balance > 0) {
            this.wallet.deposit(balance);
        }
    }

    // -------------------------------------------------------------------------
    // Thông tin vai trò
    // -------------------------------------------------------------------------

    @Override
    public String getRole() {
        return "BIDDER";
    }

    // -------------------------------------------------------------------------
    // Wallet
    // -------------------------------------------------------------------------

    /**
     * Trả về Wallet của Bidder để thực hiện freeze/release/withdraw.
     * Không trả về bản sao — caller có thể thay đổi trạng thái wallet.
     */
    public Wallet getWallet() {
        return wallet;
    }

    /** Shortcut lấy số dư khả dụng. */
    public double getBalance() {
        return wallet.getBalance();
    }

    // -------------------------------------------------------------------------
    // Polymorphism — showDetail()
    // -------------------------------------------------------------------------

    @Override
    public void showDetail() {
        printBaseInfo();
        System.out.printf("Số dư khả dụng : %,.0f VNĐ%n", wallet.getBalance());
        System.out.printf("Tiền đang cọc  : %,.0f VNĐ%n", wallet.getFrozenAmount());
        System.out.printf("Tổng ví        : %,.0f VNĐ%n", wallet.getTotalBalance());
    }

    @Override
    public String toString() {
        return String.format("Bidder{id='%s', username='%s', wallet=%s}",
                getId(), getUsername(), wallet);
    }
}
