package com.auction.common.models;

/**
 * Quản trị viên hệ thống (Admin).
 *
 * <p>Kế thừa {@link User}. Có toàn quyền giám sát và can thiệp vào
 * mọi phiên đấu giá và tài khoản người dùng.</p>
 */
public class Admin extends User {

    public Admin(String id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email);
    }

    @Override
    public String getRole() {
        return "ADMIN";
    }

    // -------------------------------------------------------------------------
    // Polymorphism — showDetail()
    // -------------------------------------------------------------------------

    @Override
    public void showDetail() {
        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║     🛡  QUẢN TRỊ VIÊN  🛡        ║");
        System.out.println("╚══════════════════════════════════╝");
        printBaseInfo();
        System.out.println("Quyền hạn: Toàn quyền hệ thống");
        System.out.println("  • Xem / đóng mọi phiên đấu giá");
        System.out.println("  • Quản lý tài khoản người dùng");
        System.out.println("  • Xem báo cáo thống kê");
    }

    @Override
    public String toString() {
        return String.format("Admin{id='%s', username='%s'}", getId(), getUsername());
    }
}
