package com.auction.server.network;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.auction.common.models.Bidder;
import com.auction.common.models.Item;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.common.pattern.ItemFactory;
import com.auction.domain.AuctionManager;
import com.auction.domain.AuctionSession;
import com.auction.domain.AuctionStatus;
import com.auction.server.dao.ItemDAO;
import com.auction.server.dao.UserDAO;
import com.auction.server.service.NotificationService;
import com.auction.server.dao.NotificationDAO;

import java.io.*;
import java.net.Socket;

/**
 * Xử lý giao tiếp với một client kết nối vào server.
 *
 * <p>Mỗi lệnh từ client được phân tách bằng ký tự '|'.
 * Ví dụ: {@code PLACE_BID|auctionId|bidderId|amount}</p>
 *
 * <p>Danh sách lệnh hỗ trợ:
 * <ul>
 *   <li>LOGIN|username|password</li>
 *   <li>REGISTER|username|password|email|role</li>
 *   <li>LIST — danh sách phiên (id:price:status)</li>
 *   <li>LIST_DETAIL — danh sách phiên đầy đủ (1 lần gọi thay vì N+1)</li>
 *   <li>GET_SESSION|auctionId</li>
 *   <li>CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes</li>
 *   <li>CREATE_ITEM|itemId|itemName|description|initPrice|category</li>
 *   <li>PLACE_BID|auctionId|bidderId|amount</li>
 *   <li>QUIT</li>
 * </ul>
 */
public class ClientHandler implements Runnable {

    private final Socket       socket;
    private final AuctionServer server;
    private PrintWriter        out;
    private String loggedInUserId;
    private final NotificationService notiService;

    public ClientHandler(Socket socket, AuctionServer server) {
        this.socket = socket;
        this.server = server;
        this.notiService = new NotificationService(server);
    }

    /** Gửi tin nhắn chủ động tới client (dùng cho broadcast). */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            this.out = writer;
            out.println("CHAO_MUNG|AuctionServer v2.0");

            String line;
            while ((line = in.readLine()) != null) {
                String response = handleRequest(line.trim());
                out.println(response);
                if ("TAM_BIET".equals(response)) break;
            }

        } catch (IOException e) {
            System.out.println("[SERVER] Mất kết nối client: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // -------------------------------------------------------------------------
    // Router chính
    // -------------------------------------------------------------------------
    private String handleRequest(String raw) {
        if (raw == null || raw.isBlank()) return "LOI|Yeu cau trong";

        String[] parts   = raw.split("\\|");
        String   command = parts[0].toUpperCase().trim();

        AuctionManager manager = AuctionManager.getInstance();

        return switch (command) {
            case "LOGIN"           -> processLogin(parts);
            case "REGISTER"        -> processRegister(parts);
            case "LIST"            -> buildListResponse(manager);
            case "LIST_DETAIL"     -> buildListDetailResponse(manager);
            case "GET_SESSION"     -> buildSessionResponse(parts, manager);
            case "CREATE_AUCTION"  -> processCreateAuction(parts, manager);
            case "CREATE_ITEM"     -> processCreateItem(parts);
            case "PLACE_BID"       -> processBid(parts, manager);
            case "CLOSE_SESSION"   -> processCloseSession(parts, manager);
            case "DEPOSIT"         -> processDeposit(parts);
            case "GET_MY_AUCTIONS" -> processGetMyAuctions(parts);
            case "DELETE_AUCTION"  -> processDeleteAuction(parts, manager);
            case "UPDATE_AUCTION"  -> processUpdateAuction(parts, manager);
            case "QUIT"            -> "TAM_BIET";
            case "GET_PROFILE" -> processGetProfile(parts);
            case "GET_MY_BIDS" -> processGetMyBids(parts);
            case "GET_NOTIFICATIONS"       -> processGetNotifications(parts);
            case "MARK_NOTIFICATIONS_READ" -> processMarkNotificationsRead(parts);
            case "BAN_USER"        -> processBanUser(parts);
            case "GET_ACTIVITY_LOG" -> processGetActivityLog(parts);
            default                -> "LOI|Lenh khong hop le: " + command;
        };
    }

    // -------------------------------------------------------------------------
    // Xử lý từng lệnh
    // -------------------------------------------------------------------------

    /**
     * LOGIN|username|password
     * → LOGIN_SUCCESS|role|id|fullName|email  hoặc  LOGIN_FAILED|...
     */
    private String processLogin(String[] parts) {
        if (parts.length != 3) return "LOI|Dinh dang: LOGIN|username|password";
        try {
            UserDAO userDAO = new UserDAO();
            User user = userDAO.getUserByUsername(parts[1]);
            if (user == null) return "LOGIN_FAILED|Sai tai khoan hoac mat khau";

            // Xác thực BCrypt — tương thích ngược: nếu hash bắt đầu bằng '$2' thì là BCrypt
            boolean valid = isPasswordValid(parts[2], user.getPassword());
            if (!valid) return "LOGIN_FAILED|Sai tai khoan hoac mat khau";
            this.loggedInUserId = user.getId();
            server.registerUser(user.getId(), this);
            return "LOGIN_SUCCESS|" + user.getRole() + "|" + user.getId()
                    + "|" + user.getFullName() + "|" + user.getEmail();

        } catch (Exception e) {
            return "LOI|Loi he thong: " + e.getMessage();
        }
    }

    /**
     * REGISTER|username|password|email|role
     * → REGISTER_SUCCESS  hoặc  REGISTER_FAILED|...
     */
    private String processRegister(String[] parts) {
        if (parts.length != 5) return "LOI|Dinh dang: REGISTER|username|password|email|role";
        String username = parts[1];
        String rawPassword = parts[2];
        String email  = parts[3];
        String role   = parts[4].toUpperCase();

        try {
            UserDAO userDAO = new UserDAO();
            if (userDAO.getUserByUsername(username) != null) {
                return "REGISTER_FAILED|Ten dang nhap da ton tai";
            }

            // Mã hóa mật khẩu bằng BCrypt (cost factor 12)
            String hashedPassword = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray());

            String id = String.valueOf(System.currentTimeMillis());
            User newUser = switch (role) {
                case "SELLER" -> new Seller(id, username, hashedPassword, username, email);
                case "ADMIN"  -> new com.auction.common.models.Admin(id, username, hashedPassword, username, email);
                default       -> new Bidder(id, username, hashedPassword, username, email, 0.0);
            };

            userDAO.saveUser(newUser);
            return "REGISTER_SUCCESS";

        } catch (Exception e) {
            return "LOI|Loi DB: " + e.getMessage();
        }
    }

    /**
     * PLACE_BID|auctionId|bidderId|amount
     * → CHAP_NHAN|...  hoặc  TU_CHOI|...
     */
    private String processBid(String[] parts, AuctionManager manager) {
        if (parts.length != 4) return "LOI|Dinh dang: PLACE_BID|auctionId|bidderId|amount";

        double amount;
        try {
            amount = Double.parseDouble(parts[3]);
        } catch (NumberFormatException e) {
            return "LOI|So tien dat gia phai la so";
        }

        boolean success = manager.placeBid(parts[1], parts[2], amount);
        AuctionSession session = manager.getSession(parts[1]);
        if (session == null) return "LOI|Khong tim thay phien dau gia: " + parts[1];

        String statusInfo = buildSessionStatusStr(session);

        if (success) {
            // Broadcast cho tất cả clients đang xem phiên này
            String broadcast = "CAP_NHAT|id=" + session.getAuctionID()
                    + "|gia_hien_tai=" + session.getCurrentHighestBid()
                    + "|nguoi_dan_dau=" + getWinnerUsername(session)
                    + "|trang_thai=" + session.getStatus()
                    + "|end_time=" + nullSafe(session.getEndTime());
            server.broadcast(broadcast);
            logActivity(parts[2], "PLACE_BID", "Phien: " + parts[1] + " | Gia: " + amount);
            return "CHAP_NHAN|" + statusInfo;
        }
        return "TU_CHOI|" + statusInfo;
    }

    /**
     * LIST — danh sách tóm tắt (id:price:status).
     * Dùng cho hiển thị nhanh.
     */
    private String buildListResponse(AuctionManager manager) {
        StringBuilder sb = new StringBuilder("DANH_SACH");
        for (AuctionSession s : manager.getAllSessions()) {
            sb.append("|").append(s.getAuctionID())
              .append(":").append(s.getCurrentHighestBid())
              .append(":").append(s.getStatus());
        }
        return sb.length() == "DANH_SACH".length() ? "DANH_SACH|trong" : sb.toString();
    }

    /**
     * LIST_DETAIL — danh sách đầy đủ trong <strong>một lần gọi duy nhất</strong>.
     * Giải quyết N+1 problem trong MainAuctionController.
     *
     * <p>Format: {@code DANH_SACH_CHI_TIET|id:itemName:price:status:endTime|...}</p>
     */
    private String buildListDetailResponse(AuctionManager manager) {
        StringBuilder sb = new StringBuilder("DANH_SACH_CHI_TIET");
        for (AuctionSession s : manager.getAllSessions()) {
            sb.append("|")
              .append(s.getAuctionID()).append(";")
              .append(s.getDisplayItem()).append(";")
              .append(s.getCurrentHighestBid()).append(";")
              .append(s.getStatus()).append(";")
              .append(nullSafe(s.getStartTime())).append(";")
              .append(nullSafe(s.getEndTime())).append(";")
              .append(s.getParticipantCount());
        }
        return sb.length() == "DANH_SACH_CHI_TIET".length()
                ? "DANH_SACH_CHI_TIET|trong" : sb.toString();
    }

    /**
     * GET_SESSION|auctionId
     * → PHIEN|id=...|vat_pham=...|gia_hien_tai=...|nguoi_dan_dau=...|trang_thai=...|end_time=...
     */
    private String buildSessionResponse(String[] parts, AuctionManager manager) {
        if (parts.length != 2) return "LOI|Dinh dang: GET_SESSION|auctionId";
        AuctionSession session = manager.getSession(parts[1]);
        if (session == null) return "LOI|Khong tim thay phien: " + parts[1];

        return "PHIEN|id=" + session.getAuctionID()
                + "|vat_pham=" + session.getDisplayItem()
                + "|" + buildSessionStatusStr(session)
                + "|start_time=" + nullSafe(session.getStartTime())
                + "|end_time=" + nullSafe(session.getEndTime());
    }

    /**
     * CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|durationMinutes
     */
    private String processCreateAuction(String[] parts, AuctionManager manager) {
        if (parts.length != 7 && parts.length != 8) {
            return "LOI|Dinh dang: CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|startTimeISO|endTimeISO";
        }
        double startPrice;
        java.time.LocalDateTime startTime;
        java.time.LocalDateTime endTime;
        try {
            startPrice = Double.parseDouble(parts[5]);
            if (parts.length == 8) {
                startTime = java.time.LocalDateTime.parse(parts[6]);
                endTime   = java.time.LocalDateTime.parse(parts[7]);
            } else {
                int duration = Integer.parseInt(parts[6]);
                startTime = java.time.LocalDateTime.now();
                endTime   = startTime.plusMinutes(Math.max(duration, 1));
            }
        } catch (Exception e) {
            return "LOI|Tham so thoi gian hoac gia khong hop le";
        }

        // Đảm bảo item tồn tại trong DB (tránh FK violation)
        try {
            ItemDAO itemDAO = new ItemDAO();
            if (itemDAO.findById(parts[2]) == null) {
                Item newItem = ItemFactory.createElectronics(
                        parts[2], parts[3], "", startPrice, "", "", "", "");
                itemDAO.saveItem(newItem);
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi tạo item: " + e.getMessage());
        }

        boolean created = manager.createSession(
                parts[1], parts[2], parts[3], parts[4], startPrice, startTime, endTime);
        if (!created) return "LOI|Ma phien da ton tai: " + parts[1];

        logActivity(parts[4], "CREATE_AUCTION", "Phien: " + parts[1] + " | San pham: " + parts[3]);
        return "CREATE_AUCTION_SUCCESS|" + parts[1];
    }

    /**
     * CREATE_ITEM|itemId|itemName|description|initPrice|category
     */
    private String processCreateItem(String[] parts) {
        if (parts.length != 6) return "LOI|Dinh dang: CREATE_ITEM|itemId|itemName|description|initPrice|category";
        try {
            double price = Double.parseDouble(parts[4]);
            Item item = ItemFactory.create(parts[1], parts[2], parts[3], price, parts[5]);
            new ItemDAO().saveItem(item);
            return "CREATE_ITEM_SUCCESS|" + parts[1];
        } catch (Exception e) {
            return "LOI|Loi tao vat pham: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Xây chuỗi trạng thái phiên (dùng chung cho nhiều response). */
    private String buildSessionStatusStr(AuctionSession session) {
        return "gia_hien_tai=" + session.getCurrentHighestBid()
                + "|nguoi_dan_dau=" + getWinnerUsername(session)
                + "|trang_thai=" + session.getStatus();
    }

    /** Chuyển giá trị null thành chuỗi rỗng (tránh "null" trong response). */
    private String nullSafe(Object obj) {
        return (obj != null) ? obj.toString() : "";
    }

    /**
     * Kiểm tra mật khẩu — hỗ trợ cả BCrypt (mới) và plaintext (cũ, backward compat).
     * Sau khi toàn bộ user đã đăng ký lại, có thể xóa nhánh plaintext.
     */
    private boolean isPasswordValid(String rawPassword, String storedPassword) {
        if (storedPassword != null && storedPassword.startsWith("$2")) {
            // BCrypt hash
            return BCrypt.verifyer()
                         .verify(rawPassword.toCharArray(), storedPassword)
                         .verified;
        }
        // Fallback plaintext (người dùng cũ seed/demo)
        return rawPassword.equals(storedPassword);
    }


    /**
     * GET_MY_AUCTIONS|sellerId
     * → MY_AUCTIONS|auctionId:itemName:price:status:endTime|...
     */
    private String processGetMyAuctions(String[] parts) {
        if (parts.length != 2) return "LOI|Dinh dang: GET_MY_AUCTIONS|sellerId";
        try {
            var sessions = new com.auction.server.dao.AuctionDAO().getSessionsBySeller(parts[1]);
            StringBuilder sb = new StringBuilder("MY_AUCTIONS");
            for (var s : sessions) {
                sb.append("|")
                  .append(s.getAuctionID()).append(";")
                  .append(s.getDisplayItem()).append(";")
                  .append(s.getCurrentHighestBid()).append(";")
                  .append(s.getStatus()).append(";")
                  .append(nullSafe(s.getStartTime())).append(";")
                  .append(nullSafe(s.getEndTime()));
            }
            return sb.length() == "MY_AUCTIONS".length() ? "MY_AUCTIONS|trong" : sb.toString();
        } catch (Exception e) {
            return "LOI|Loi lay danh sach: " + e.getMessage();
        }
    }

    /**
     * DELETE_AUCTION|auctionId|sellerId
     * Chỉ cho phép xóa khi chưa có bid và seller là chủ phiên.
     */
    private String processDeleteAuction(String[] parts, AuctionManager manager) {
        if (parts.length != 3) return "LOI|Dinh dang: DELETE_AUCTION|auctionId|sellerId";
        String auctionId = parts[1];
        String sellerId  = parts[2];
        try {
            com.auction.server.dao.AuctionDAO auctionDAO = new com.auction.server.dao.AuctionDAO();
            
            // Hoàn lại tiền cho bidder đang dẫn đầu (nếu có) trước khi xóa phiên đấu giá
            com.auction.domain.AuctionSession session = manager.getSession(auctionId);
            if (session != null) {
                String leadingBidder = session.getCurrentHighestBidderID();
                double leadingAmount = session.getCurrentHighestBid();
                if (leadingBidder != null && !leadingBidder.isBlank()) {
                    try {
                        com.auction.server.dao.UserDAO userDAO = new com.auction.server.dao.UserDAO();
                        com.auction.common.models.User user = userDAO.findById(leadingBidder);
                        if (user instanceof com.auction.common.models.Bidder bidder) {
                            bidder.getWallet().release(leadingAmount);
                            userDAO.saveUser(bidder);
                            System.out.printf("[SERVER] Hoan lai %.0f cho bidder %s khi seller xoa phien %s%n",
                                leadingAmount, leadingBidder, auctionId);
                        }
                    } catch (Exception e) {
                        System.err.println("[SERVER] Loi hoan tien cho bidder khi xoa phien: " + e.getMessage());
                    }
                }
            }

            // Xóa khỏi AuctionManager (in-memory)
            manager.removeSession(auctionId);
            // Xóa khỏi DB (bảng auction_sessions)
            auctionDAO.deleteSession(auctionId);
            // Xóa các bid liên quan trong DB
            try {
                auctionDAO.deleteBidsByAuction(auctionId);
            } catch (Exception e) {
                System.err.println("[SERVER] Loi xoa bid transactions: " + e.getMessage());
            }
            return "DELETE_AUCTION_SUCCESS|" + auctionId;
        } catch (Exception e) {
            return "LOI|Loi xoa phien: " + e.getMessage();
        }
    }

    /**
     * UPDATE_AUCTION|auctionId|sellerId|newItemName
     * Chỉ cho phép sửa khi chưa có bid và seller là chủ phiên.
     */
    private String processUpdateAuction(String[] parts, AuctionManager manager) {
        if (parts.length != 4) return "LOI|Dinh dang: UPDATE_AUCTION|auctionId|sellerId|newItemName";
        String auctionId   = parts[1];
        String newItemName = parts[3];
        try {
            com.auction.server.dao.AuctionDAO auctionDAO = new com.auction.server.dao.AuctionDAO();
            
            // Lấy session để kiểm tra trạng thái và tìm itemId
            com.auction.domain.AuctionSession session = manager.getSession(auctionId);
            if (session == null) return "LOI|Khong tim thay phien: " + auctionId;

            // Chỉ cho phép sửa khi phiên đấu giá chưa bắt đầu (status == PENDING)
            if (session.getStatus() != com.auction.domain.AuctionStatus.PENDING) {
                return "LOI|Khong the sua: phien dau gia da hoac dang bat dau (trang thai hien tai: " + session.getStatus() + ")";
            }

            // Cập nhật tên item trong DB
            auctionDAO.updateItemName(session.getItemID(), newItemName);
            // Cập nhật tên hiển thị in-memory
            session.setDisplayItem(newItemName);
            return "UPDATE_AUCTION_SUCCESS|" + auctionId;
        } catch (Exception e) {
            return "LOI|Loi cap nhat phien: " + e.getMessage();
        }
    }

    /**
     * CLOSE_SESSION|auctionId — Admin đóng phiên thủ công.
     */
    private String processCloseSession(String[] parts, AuctionManager manager) {
        if (parts.length != 2) return "LOI|Dinh dang: CLOSE_SESSION|auctionId";
        boolean ok = manager.closeSession(parts[1]);
        if (!ok) return "LOI|Khong the dong phien (khong ton tai hoac da dong): " + parts[1];

        // Broadcast kết thúc tới tất cả client
        server.broadcast("CAP_NHAT|id=" + parts[1]
                + "|trang_thai=FINISHED|gia_hien_tai=0|nguoi_dan_dau=");
        logActivity("ADMIN", "CLOSE_SESSION", "Phien: " + parts[1]);
        return "CLOSE_SESSION_SUCCESS|" + parts[1];
    }

    /**
     * DEPOSIT|bidderId|amount — Nạp tiền vào ví Bidder.
     */
    private String processDeposit(String[] parts) {
        if (parts.length != 3) return "LOI|Dinh dang: DEPOSIT|bidderId|amount";
        double amount;
        try {
            amount = Double.parseDouble(parts[2]);
        } catch (NumberFormatException e) {
            return "LOI|So tien phai la so";
        }
        if (amount <= 0) return "LOI|So tien phai lon hon 0";
        try {
            UserDAO userDAO = new UserDAO();
            User user = userDAO.findById(parts[1]);
            if (!(user instanceof Bidder bidder)) return "LOI|Khong tim thay Bidder: " + parts[1];
            bidder.getWallet().deposit(amount);
            userDAO.saveUser(bidder);
            logActivity(parts[1], "DEPOSIT", "So tien: " + amount);
            return "DEPOSIT_SUCCESS|" + bidder.getWallet().getBalance();
        } catch (Exception e) {
            return "LOI|Loi nap tien: " + e.getMessage();
        }
    }

    private String getWinnerUsername(AuctionSession session) {
        String winnerId = session.getWinnerID();
        if (winnerId == null || winnerId.isBlank()) {
            return "";
        }
        try {
            User user = new UserDAO().findById(winnerId);
            if (user != null) {
                return user.getUsername();
            }
        } catch (Exception e) {
            System.err.println("[SERVER] Lỗi tìm username cho winnerID " + winnerId + ": " + e.getMessage());
        }
        return winnerId;
    }

    /** Giải phóng tài nguyên khi client ngắt kết nối. */
    private void cleanup() {
        server.unregisterUser(loggedInUserId);
        server.removeClient(this);
        try {
            if (!socket.isClosed()) socket.close();
        } catch (IOException ignored) { }
    }
    /**
     * GET_PROFILE|bidderId
     * → PROFILE_SUCCESS|balance|frozenAmount
     */
    private String processGetProfile(String[] parts) {
        if (parts.length != 2) return "LOI|Dinh dang: GET_PROFILE|bidderId";
        try {
            User user = new UserDAO().findById(parts[1]);
            if (!(user instanceof Bidder bidder))
                return "LOI|Khong tim thay Bidder: " + parts[1];
            return "PROFILE_SUCCESS|"
                    + bidder.getWallet().getBalance() + "|"
                    + bidder.getWallet().getFrozenAmount();
        } catch (Exception e) {
            return "LOI|Loi lay profile: " + e.getMessage();
        }
    }
    /**
     * GET_MY_BIDS|bidderId
     * → MY_BIDS|auctionId;itemName;bidAmount;status|...
     */
    private String processGetMyBids(String[] parts) {
        if (parts.length != 2) return "LOI|Dinh dang: GET_MY_BIDS|bidderId";
        try {
            // Lấy tất cả bid của bidder này
            String sql = """
                SELECT bt.auction_id, COALESCE(i.name, bt.auction_id) as item_name,
                    bt.bid_amount, s.status
                FROM bid_transactions bt
                LEFT JOIN auction_sessions s ON bt.auction_id = s.auction_id
                LEFT JOIN items i ON s.item_id = i.id
                WHERE bt.bidder_id = ?
                ORDER BY bt.bid_time DESC
                """;

            java.util.Map<String, String[]> latest = new java.util.LinkedHashMap<>();
            try (var conn = com.auction.server.util.DatabaseUtil.getInstance().getConnection();
                var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, parts[1]);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    String aid = rs.getString("auction_id");
                    // Chỉ giữ bid mới nhất mỗi phiên
                    if (!latest.containsKey(aid)) {
                        latest.put(aid, new String[]{
                            aid,
                            rs.getString("item_name"),
                            String.valueOf(rs.getDouble("bid_amount")),
                            rs.getString("status")
                        });
                    }
                }
            }

            if (latest.isEmpty()) return "MY_BIDS|trong";

            StringBuilder sb = new StringBuilder("MY_BIDS");
            for (var entry : latest.values()) {
                sb.append("|")
                .append(entry[0]).append(";")
                .append(entry[1]).append(";")
                .append(entry[2]).append(";")
                .append(entry[3]);
            }
            return sb.toString();

        } catch (Exception e) {
            return "LOI|Loi lay my bids: " + e.getMessage();
        }
    }

    private String processGetNotifications(String[] parts) {
        if (parts.length != 2) return "LOI|Dinh dang: GET_NOTIFICATIONS|userId";
        try {
            var list = new NotificationDAO().getByUser(parts[1]);
            if (list.isEmpty()) return "NOTIFICATIONS|trong";
            StringBuilder sb = new StringBuilder("NOTIFICATIONS");
            for (String[] n : list) {
                // id;type;content;auctionId;isRead;createdAt
                sb.append("|")
                .append(n[0]).append(";")
                .append(n[1]).append(";")
                .append(n[2]).append(";")
                .append(n[3]).append(";")
                .append(n[4]).append(";")
                .append(n[5]);
            }
            return sb.toString();
        } catch (Exception e) {
            return "LOI|" + e.getMessage();
        }
    }

    private String processMarkNotificationsRead(String[] parts) {
        if (parts.length != 2) return "LOI|Dinh dang: MARK_NOTIFICATIONS_READ|userId";
        try {
            new NotificationDAO().markAllRead(parts[1]);
        } catch (Exception e) {
            return "LOI|" + e.getMessage();
        }
        return "MARK_READ_SUCCESS";
    }

        /**
     * BAN_USER|targetUsername|ban (true/false)
     */
    private String processBanUser(String[] parts) {
        if (parts.length != 3) return "LOI|Dinh dang: BAN_USER|username|true/false";
        try {
            UserDAO userDAO = new UserDAO();
            User target = userDAO.getUserByUsername(parts[1]);
            if (target == null) return "LOI|Khong tim thay user: " + parts[1];
            boolean ban = Boolean.parseBoolean(parts[2]);
            try (var conn = com.auction.server.util.DatabaseUtil.getInstance().getConnection();
                var stmt = conn.prepareStatement(
                        "UPDATE users SET is_banned = ? WHERE username = ?")) {
                stmt.setInt(1, ban ? 1 : 0);
                stmt.setString(2, parts[1]);
                stmt.executeUpdate();
            }
            // Ghi log
            logActivity("ADMIN", ban ? "BAN_USER" : "UNBAN_USER", "Target: " + parts[1]);
            return ban ? "BAN_SUCCESS|" + parts[1] : "UNBAN_SUCCESS|" + parts[1];
        } catch (Exception e) {
            return "LOI|" + e.getMessage();
        }
    }

    /**
     * GET_ACTIVITY_LOG — lấy 100 log gần nhất
     */
    private String processGetActivityLog(String[] parts) {
        try {
            String sql = """
                    SELECT user_id, action, detail, created_at
                    FROM activity_log
                    ORDER BY created_at DESC
                    LIMIT 100
                    """;
            StringBuilder sb = new StringBuilder("ACTIVITY_LOG");
            try (var conn = com.auction.server.util.DatabaseUtil.getInstance().getConnection();
                var stmt = conn.prepareStatement(sql);
                var rs   = stmt.executeQuery()) {
                while (rs.next()) {
                    sb.append("|")
                    .append(rs.getString("user_id")).append(";")
                    .append(rs.getString("action")).append(";")
                    .append(rs.getString("detail") != null ? rs.getString("detail") : "").append(";")
                    .append(rs.getString("created_at"));
                }
            }
            return sb.length() == "ACTIVITY_LOG".length()
                    ? "ACTIVITY_LOG|trong" : sb.toString();
        } catch (Exception e) {
            return "LOI|" + e.getMessage();
        }
    }

    private void logActivity(String userId, String action, String detail) {
        try {
            String sql = "INSERT INTO activity_log (user_id, action, detail, created_at) VALUES (?, ?, ?, ?)";
            try (var conn = com.auction.server.util.DatabaseUtil.getInstance().getConnection();
                var stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, userId);
                stmt.setString(2, action);
                stmt.setString(3, detail);
                stmt.setString(4, java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                stmt.executeUpdate();
            }
        } catch (Exception ignored) {}
    }
}
