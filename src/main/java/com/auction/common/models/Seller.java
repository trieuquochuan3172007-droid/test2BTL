package com.auction.common.models;

/**
 * Người bán hàng (Seller) — có quyền tạo phiên đấu giá và quản lý sản phẩm.
 *
 * <p>Kế thừa {@link User}. Không có Wallet — Seller không đặt giá,
 * chỉ nhận thanh toán khi phiên kết thúc.</p>
 */
public class Seller extends User {

    public Seller(String id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email);
    }

    @Override
    public String getRole() {
        return "SELLER";
    }

    // -------------------------------------------------------------------------
    // Polymorphism — showDetail()
    // -------------------------------------------------------------------------

    @Override
    public void showDetail() {
        printBaseInfo();
        System.out.println("Quyền hạn: Tạo phiên đấu giá | Quản lý sản phẩm");
    }

    @Override
    public String toString() {
        return String.format("Seller{id='%s', username='%s'}", getId(), getUsername());
    }
}
