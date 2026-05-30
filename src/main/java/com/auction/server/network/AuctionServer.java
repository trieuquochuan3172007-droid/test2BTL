package com.auction.server.network;

import com.auction.common.models.User;
import com.auction.domain.AuctionManager;
import com.auction.server.util.DatabaseUtil;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.auction.server.dao.NotificationDAO;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server lắng nghe kết nối TCP từ các client đấu giá.
 *
 * <p>Mỗi client được phục vụ bởi một thread riêng từ fixed thread pool (50 threads).
 * Sử dụng {@link AuctionManager} singleton để quản lý tất cả phiên đấu giá.</p>
 */
public class AuctionServer {

    public static final int DEFAULT_PORT = 9999;

    private final int          port;
    private final Set<ClientHandler> clients    = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();
    private final ExecutorService    threadPool = Executors.newFixedThreadPool(50);

    public AuctionServer(int port) {
        this.port = port;
    }

    // -------------------------------------------------------------------------
    // Khởi động server
    // -------------------------------------------------------------------------
    public void start() throws IOException {
        initializeDatabase();
        seedDemoData();

        // Đăng ký callback cho AuctionManager để broadcast cho các client
        AuctionManager.getInstance().setBroadcastStatusCallback((auctionId, status) -> {
            com.auction.domain.AuctionSession s = AuctionManager.getInstance().getSession(auctionId);
            String endTimeStr = (s != null && s.getEndTime() != null) ? s.getEndTime().toString() : "";
            broadcast("CAP_NHAT|id=" + auctionId + "|trang_thai=" + status + "|end_time=" + endTimeStr);
        });
        AuctionManager.getInstance().setBroadcastExtensionCallback((auctionId, endTimeISO) -> {
            broadcast("CAP_NHAT|id=" + auctionId + "|end_time=" + endTimeISO);
        });

        // Shutdown hook: lưu data và tắt pool sạch sẽ
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[SERVER] ⚠ Đang tắt — lưu dữ liệu...");
            AuctionManager.getInstance().persistAllSessions();
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
            }
            DatabaseUtil.getInstance().shutdown();
            System.out.println("[SERVER] ✓ Đã tắt an toàn.");
        }, "shutdown-hook"));

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("[SERVER] ✓ Đang lắng nghe ở cổng " + port);
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[SERVER] Client kết nối: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                threadPool.execute(handler);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Broadcast tới tất cả clients
    // -------------------------------------------------------------------------
    public void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
    }

    public void sendToUser(String userId, String message) {
    ClientHandler handler = onlineUsers.get(userId);
    if (handler != null) handler.sendMessage(message);
    }

    public void registerUser(String userId, ClientHandler handler) {
        onlineUsers.put(userId, handler);
    }
    
    public void unregisterUser(String userId) {
        if (userId != null) onlineUsers.remove(userId);
    }

    // -------------------------------------------------------------------------
    // Dữ liệu mẫu cho demo
    // -------------------------------------------------------------------------
    private void seedDemoData() {
        AuctionManager manager = AuctionManager.getInstance();
        com.auction.domain.AuctionSession a1 = manager.getSession("A1");
        if (a1 != null) {
            System.out.println("[SERVER] Làm mới thời gian cho phiên mẫu A1.");
            a1.setStartTime(java.time.LocalDateTime.now());
            a1.setEndTime(java.time.LocalDateTime.now().plusMinutes(60));
            a1.setStatus(com.auction.domain.AuctionStatus.RUNNING);
            // Cập nhật vào DB
            try { new com.auction.server.dao.AuctionDAO().saveSession(a1); } catch (Exception ignored) {}
            return;
        }
        try {
            com.auction.server.dao.ItemDAO itemDAO = new com.auction.server.dao.ItemDAO();
            if (itemDAO.findById("ITEM1") == null) {
                itemDAO.saveItem(com.auction.common.pattern.ItemFactory.createElectronics(
                        "ITEM1", "Laptop Demo", "Laptop mẫu cho demo",
                        500.0, "DemoTech", "X1", "SN-001", "2027-01-01"));
            }

            com.auction.server.dao.UserDAO userDAO = new com.auction.server.dao.UserDAO();
            User existingAdmin = userDAO.getUserByUsername("admin");
            if (existingAdmin != null && !existingAdmin.getPassword().startsWith("$2")) {
                try (var conn = com.auction.server.util.DatabaseUtil.getInstance().getConnection();
                    var stmt = conn.prepareStatement("DELETE FROM users WHERE username = 'admin'")) {
                    stmt.executeUpdate();
                }
                existingAdmin = null;
            }
            if (existingAdmin == null) {
                String hashedAdmin = BCrypt.withDefaults()
                        .hashToString(12, "Admin@2026".toCharArray());
                userDAO.saveUser(new com.auction.common.models.Admin(
                        "ADMIN1", "admin", hashedAdmin, "Quan tri vien", "admin@auction.com"));
                System.out.println("[SERVER] Da tao tai khoan Admin mac dinh (admin/Admin@2026)");
            }
            if (userDAO.getUserByUsername("SELLER1") == null) {
                userDAO.saveUser(new com.auction.common.models.Seller(
                        "SELLER1", "SELLER1", "seller123", "Seller Demo", "seller@demo.com"));
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi seed dữ liệu: " + e.getMessage());
        }

        if (manager.createSession("A1", "ITEM1", "Laptop Demo", "SELLER1", 500.0, 60)) {
            manager.startSession("A1");
            System.out.println("[SERVER] ✓ Đã tạo phiên mẫu A1.");
        }
    }

    // -------------------------------------------------------------------------
    // Khởi tạo Database — đọc credentials từ application.properties
    // -------------------------------------------------------------------------
    private void initializeDatabase() {
        Properties props = loadProperties();
        String dbUrl      = props.getProperty("db.url", "jdbc:mysql://localhost:3306/auction_system");
        String dbUsername = props.getProperty("db.username", "root");
        String dbPassword = props.getProperty("db.password", "");

        // Tạo database nếu chưa có (kết nối root không chỉ định DB)
        String rootUrl = dbUrl.replaceAll("/auction_system.*", "/")
                              + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        try (Connection conn = DriverManager.getConnection(rootUrl, dbUsername, dbPassword);
             Statement stmt  = conn.createStatement()) {

            stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS auction_system");
            System.out.println("[SERVER] ✓ Database 'auction_system' đã sẵn sàng.");

        } catch (Exception e) {
            System.err.println("[SERVER] ✗ Không thể tạo database: " + e.getMessage());
        }

        // Tạo bảng qua HikariCP pool (kết nối đúng vào auction_system)
        try (Connection conn = DatabaseUtil.getInstance().getConnection();
             Statement stmt  = conn.createStatement()) {

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        id            VARCHAR(50)  PRIMARY KEY,
                        username      VARCHAR(50)  NOT NULL UNIQUE,
                        password      VARCHAR(255) NOT NULL,
                        full_name     VARCHAR(100) NOT NULL,
                        email         VARCHAR(100) NOT NULL UNIQUE,
                        role          VARCHAR(20)  NOT NULL,
                        balance       DOUBLE       DEFAULT 0,
                        frozen_amount DOUBLE       DEFAULT 0
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS items (
                        id          VARCHAR(50)  PRIMARY KEY,
                        name        VARCHAR(100) NOT NULL,
                        description TEXT,
                        init_price  DOUBLE       NOT NULL,
                        category    VARCHAR(50)  NOT NULL
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS auction_sessions (
                        auction_id          VARCHAR(50) PRIMARY KEY,
                        item_id             VARCHAR(50) NOT NULL,
                        seller_id           VARCHAR(50) NOT NULL,
                        start_time          DATETIME    NOT NULL,
                        end_time            DATETIME    NOT NULL,
                        status              VARCHAR(20) NOT NULL,
                        winner_id           VARCHAR(50),
                        current_highest_bid DOUBLE      DEFAULT 0
                    )""");

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bid_transactions (
                        id         INT AUTO_INCREMENT PRIMARY KEY,
                        auction_id VARCHAR(50) NOT NULL,
                        bidder_id  VARCHAR(50) NOT NULL,
                        bid_amount DOUBLE      NOT NULL,
                        bid_time   DATETIME    NOT NULL
                    )""");
            // Thêm cột frozen_amount
            new NotificationDAO().createTableIfNotExists();
            // Bảng activity log
            try {
                stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS activity_log (
                            id         INT AUTO_INCREMENT PRIMARY KEY,
                            user_id    VARCHAR(50)  NOT NULL,
                            action     VARCHAR(100) NOT NULL,
                            detail     TEXT,
                            created_at DATETIME     NOT NULL
                        )""");
            } catch (Exception ignored) {}

            // Cột is_banned cho bảng users
            try {
                stmt.executeUpdate(
                        "ALTER TABLE users ADD COLUMN is_banned TINYINT(1) DEFAULT 0");
            } catch (Exception ignored) {}
            // Thêm cột frozen_amount nếu DB cũ chưa có (migration an toàn)
            try {
                stmt.executeUpdate(
                        "ALTER TABLE users ADD COLUMN frozen_amount DOUBLE DEFAULT 0");
                System.out.println("[SERVER] ✓ Đã thêm cột frozen_amount vào bảng users.");
            } catch (Exception ignored) {
                // Cột đã tồn tại — bỏ qua lỗi ALTER TABLE
            }

            System.out.println("[SERVER] ✓ Database đã khởi tạo hoàn tất.");

        } catch (Exception e) {
            System.err.println("[SERVER] ✗ Lỗi khởi tạo bảng: " + e.getMessage());
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config/application.properties")) {
            if (in != null) props.load(in);
        } catch (Exception e) {
            System.err.println("[SERVER] Không đọc được properties: " + e.getMessage());
        }
        return props;
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------
    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        try {
            new AuctionServer(port).start();
        } catch (IOException e) {
            System.err.println("[SERVER] ✗ Lỗi khởi động: " + e.getMessage());
        }
    }
}
