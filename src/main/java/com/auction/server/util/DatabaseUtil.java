package com.auction.server.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Singleton cung cấp Connection Pool (HikariCP) cho toàn bộ server.
 *
 * <p>Thay thế design cũ (một Connection duy nhất) vốn không thread-safe
 * khi nhiều ClientHandler chạy song song.</p>
 *
 * <p>Mỗi lần gọi {@link #getConnection()} trả về một Connection riêng từ pool.
 * Caller PHẢI đóng Connection bằng try-with-resources hoặc {@code conn.close()}
 * để trả Connection về pool.</p>
 */
public final class DatabaseUtil {

    private static volatile DatabaseUtil instance;
    private final HikariDataSource dataSource;

    // -------------------------------------------------------------------------
    // Khởi tạo (private constructor — Singleton Pattern)
    // -------------------------------------------------------------------------
    private DatabaseUtil() {
        Properties props = loadProperties();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(props.getProperty("db.url",
                "jdbc:mysql://localhost:3306/auction_system?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"));
        config.setUsername(props.getProperty("db.username", "root"));
        config.setPassword(props.getProperty("db.password", ""));

        // Kích thước pool: tối đa 20, giữ sẵn 5 connection nhàn rỗi
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);

        // Timeout đợi lấy connection từ pool (30 giây)
        config.setConnectionTimeout(30_000);
        // Đóng connection nhàn rỗi sau 10 phút
        config.setIdleTimeout(600_000);
        // Vòng đời tối đa của mỗi connection (30 phút) — tránh stale connection
        config.setMaxLifetime(1_800_000);
        // Câu SQL kiểm tra connection còn sống không
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("AuctionPool");

        this.dataSource = new HikariDataSource(config);
        System.out.println("[DATABASE] ✓ Connection Pool khởi tạo thành công (max=" + config.getMaximumPoolSize() + ")");
    }

    // -------------------------------------------------------------------------
    // Double-Checked Locking Singleton
    // -------------------------------------------------------------------------
    public static DatabaseUtil getInstance() {
        if (instance == null) {
            synchronized (DatabaseUtil.class) {
                if (instance == null) {
                    instance = new DatabaseUtil();
                }
            }
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Lấy một Connection từ pool.
     * <strong>Phải đóng Connection sau khi dùng xong</strong> (dùng try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        if (isJUnitTest()) {
            throw new SQLException("Database disabled in JUnit test mode to prevent data pollution.");
        }
        return dataSource.getConnection();
    }

    private static boolean isJUnitTest() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().startsWith("org.junit.")) {
                return true;
            }
        }
        return false;
    }

    /** Đóng toàn bộ pool — gọi khi server tắt. */
    public void shutdown() {
        if (!dataSource.isClosed()) {
            dataSource.close();
            System.out.println("[DATABASE] Pool đã đóng.");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/application.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                System.err.println("[DATABASE] ⚠ Không tìm thấy application.properties, dùng giá trị mặc định.");
            }
        } catch (Exception e) {
            System.err.println("[DATABASE] ⚠ Lỗi đọc properties: " + e.getMessage());
        }
        return props;
    }
}
