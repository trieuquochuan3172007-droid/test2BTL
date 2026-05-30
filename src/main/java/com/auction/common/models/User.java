package com.auction.common.models;

import java.io.Serializable;

/**
 * Lớp trừu tượng đại diện cho người dùng trong hệ thống.
 *
 * <p>Kế thừa {@link Entity} — có sẵn {@code id}, {@code equals()},
 * {@code hashCode()}, và phương thức trừu tượng {@link #showDetail()}.</p>
 *
 * <p>Các lớp con: {@link Bidder}, {@link Seller}, {@link Admin}.</p>
 *
 * <p>Áp dụng Polymorphism: {@link #showDetail()} và {@link #getRole()}
 * được override ở mỗi lớp con theo đặc thù vai trò.</p>
 */
public abstract class User extends Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;  // Lưu BCrypt hash — không bao giờ so sánh trực tiếp
    private String fullName;
    private String email;

    protected User(String id, String username, String password, String fullName, String email) {
        super(id);
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.email    = email;
    }

    // -------------------------------------------------------------------------
    // Getters (password không có setter — bảo mật)
    // -------------------------------------------------------------------------
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFullName() { return fullName; }
    public String getEmail()    { return email;    }

    public void setUsername(String username) { this.username = username; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public void setEmail(String email)       { this.email    = email;    }

    // -------------------------------------------------------------------------
    // Phương thức trừu tượng — Polymorphism
    // -------------------------------------------------------------------------

    /**
     * Trả về vai trò của người dùng: "BIDDER", "SELLER", hoặc "ADMIN".
     * Mỗi lớp con override để cung cấp giá trị đặc thù.
     */
    public abstract String getRole();

    /**
     * Hiển thị thông tin chi tiết người dùng ra console.
     * Override từ {@link Entity#showDetail()} — mỗi lớp con in thông tin đặc thù.
     */
    @Override
    public abstract void showDetail();

    // -------------------------------------------------------------------------
    // Thông tin chung
    // -------------------------------------------------------------------------

    /**
     * In thông tin cơ bản — lớp con gọi {@code super.showDetail()} để tái dùng.
     */
    protected void printBaseInfo() {
        System.out.println("═══════════════════════════════");
        System.out.printf("Người dùng : %s [%s]%n", fullName, getRole());
        System.out.printf("Username   : %s%n", username);
        System.out.printf("Email      : %s%n", email);
        System.out.printf("ID         : %s%n", id);
        System.out.println("═══════════════════════════════");
    }

    @Override
    public String toString() {
        return String.format("User{id='%s', username='%s', role='%s'}", id, username, getRole());
    }
}
