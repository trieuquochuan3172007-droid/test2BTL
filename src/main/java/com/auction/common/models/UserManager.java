package com.auction.common.models;

/**
 * Singleton lưu thông tin phiên đăng nhập hiện tại trên client.
 *
 * <p>Không lưu xuống file — chỉ tồn tại trong bộ nhớ khi ứng dụng đang chạy.
 * Khi client tắt hoặc đăng xuất, trạng thái bị xóa.</p>
 *
 * <p>Singleton Pattern — Double-Checked Locking.</p>
 */
public class UserManager {

    private static volatile UserManager instance;
    private User currentUser;

    private UserManager() {}

    public static UserManager getInstance() {
        if (instance == null) {
            synchronized (UserManager.class) {
                if (instance == null) instance = new UserManager();
            }
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Session management
    // -------------------------------------------------------------------------

    /** Lấy người dùng đang đăng nhập; null nếu chưa đăng nhập. */
    public User getCurrentUser() { return currentUser; }

    /** Đặt người dùng sau khi đăng nhập thành công. */
    public void setCurrentUser(User user) { this.currentUser = user; }

    /** Đăng xuất — xóa thông tin người dùng hiện tại. */
    public void logout() { this.currentUser = null; }

    /** Kiểm tra người dùng có đang đăng nhập không. */
    public boolean isLoggedIn() { return currentUser != null; }

    /** Sinh ID duy nhất dựa trên timestamp hiện tại. */
    public String generateId() {
        return String.valueOf(System.currentTimeMillis());
    }
}
