package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.UserManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller cho panel Notification (hiển thị trong sidebar/overlay).
 *
 * <p>Nhúng vào MainAuction.fxml và AdminDashboard.fxml qua fx:include.</p>
 *
 * <p>Luồng:
 * <ol>
 *   <li>{@link #initialize()} — tải danh sách từ DB qua GET_NOTIFICATIONS.</li>
 *   <li>{@link #onRealtimeNotification(String)} — nhận push từ NetworkClient
 *       (được gọi từ MainAuctionController sau khi set notificationListener).</li>
 *   <li>{@link #handleMarkAllRead()} — đánh dấu đã đọc + cập nhật badge.</li>
 * </ol>
 * </p>
 */
public class NotificationController {

    // FXML — panel chứa danh sách
    @FXML private Label              lblBadge;       // Badge số chưa đọc (trên bell icon)
    @FXML private Label              lblUnreadCount; // Label "X chưa đọc" trong panel
    @FXML private ListView<String[]> listNotifications;

    private final ObservableList<String[]> notiData =
            FXCollections.observableArrayList();

    private String userId;
    private int    unreadCount = 0;

    // -------------------------------------------------------------------------
    // Khởi tạo
    // -------------------------------------------------------------------------
    @FXML
    public void initialize() {
        var user = UserManager.getInstance().getCurrentUser();
        if (user == null) return;
        userId = user.getId();

        listNotifications.setItems(notiData);
        listNotifications.setCellFactory(lv -> new NotificationCell());

        loadNotificationsAsync();
    }

    // -------------------------------------------------------------------------
    // Load từ DB
    // -------------------------------------------------------------------------
    private void loadNotificationsAsync() {
        new Thread(() -> {
            String response = NetworkClient.getInstance()
                    .sendRequest("GET_NOTIFICATIONS|" + userId);
            Platform.runLater(() -> parseAndDisplay(response));
        }).start();
    }

    // -------------------------------------------------------------------------
    // Nhận realtime push từ server (gọi từ MainAuctionController)
    // -------------------------------------------------------------------------

    /**
     * Được gọi khi server push NOTIFICATION|type|content|auctionId.
     * Phải gọi từ JavaFX thread (Platform.runLater bọc ở ngoài).
     *
     * @param message raw socket message
     */
    public void onRealtimeNotification(String message) {
        // Format: NOTIFICATION|type|content|auctionId
        String[] parts = message.split("\\|", 4);
        if (parts.length < 3) return;

        String type      = parts[1];
        String content   = parts[2];
        String auctionId = parts.length > 3 ? parts[3] : "";
        String now       = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm dd/MM"));

        // [id="-1" để phân biệt chưa lưu từ DB (server đã lưu DB rồi, ở đây chỉ hiển thị)]
        String[] row = {"-1", type, content, auctionId, "0", now};

        Platform.runLater(() -> {
            notiData.add(0, row); // Thêm vào đầu danh sách
            unreadCount++;
            updateBadge();
        });
    }

    // -------------------------------------------------------------------------
    // Đánh dấu tất cả đã đọc
    // -------------------------------------------------------------------------
    @FXML
    public void handleMarkAllRead() {
        new Thread(() -> {
            NetworkClient.getInstance()
                    .sendRequest("MARK_NOTIFICATIONS_READ|" + userId);
            Platform.runLater(() -> {
                // Cập nhật trạng thái isRead trong danh sách
                for (String[] row : notiData) {
                    row[4] = "1";
                }
                listNotifications.refresh();
                unreadCount = 0;
                updateBadge();
            });
        }).start();
    }

    // -------------------------------------------------------------------------
    // Parse response từ server
    // -------------------------------------------------------------------------

    /**
     * Parse response GET_NOTIFICATIONS.
     * Format: NOTIFICATIONS|id;type;content;auctionId;isRead;createdAt|...
     */
    private void parseAndDisplay(String response) {
        notiData.clear();
        unreadCount = 0;
        if (response == null || "NOTIFICATIONS|trong".equals(response)) {
            updateBadge();
            return;
        }
        if (!response.startsWith("NOTIFICATIONS")) return;

        String[] entries = response.split("\\|");
        for (int i = 1; i < entries.length; i++) {
            String[] cols = entries[i].split(";", 6);
            if (cols.length < 6) continue;
            notiData.add(cols);
            if ("0".equals(cols[4])) unreadCount++;
        }
        updateBadge();
    }

    // -------------------------------------------------------------------------
    // Cập nhật badge
    // -------------------------------------------------------------------------
    private void updateBadge() {
        if (lblBadge != null) {
            if (unreadCount > 0) {
                lblBadge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
                lblBadge.setVisible(true);
            } else {
                lblBadge.setVisible(false);
            }
        }
        if (lblUnreadCount != null) {
            lblUnreadCount.setText(unreadCount > 0
                    ? unreadCount + " chưa đọc" : "Tất cả đã đọc");
        }
    }

    /** Getter cho badge count — dùng bởi MainAuctionController để cập nhật sidebar. */
    public int getUnreadCount() {
        return unreadCount;
    }

    // =========================================================================
    // Custom ListCell
    // =========================================================================

    /**
     * Cell hiển thị một notification với icon theo type, nội dung, thời gian
     * và màu nền khác nhau cho đã đọc / chưa đọc.
     */
    private static class NotificationCell extends ListCell<String[]> {

        @Override
        protected void updateItem(String[] item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                setStyle("");
                return;
            }

            // item: [id, type, content, auctionId, isRead, createdAt]
            String type    = item[1];
            String content = item[2];
            String time    = item.length > 5 ? item[5] : "";
            boolean isRead = "1".equals(item[4]);

            // Icon theo loại
            String icon = switch (type) {
                case "BID_OUTBID"      -> "📢";
                case "AUCTION_WON"     -> "🏆";
                case "AUCTION_LOST"    -> "😔";
                case "AUCTION_ENDING"  -> "⏰";
                case "ANTI_SNIPE"      -> "🔔";
                case "BID_PLACED"      -> "💰";
                case "SESSION_CREATED" -> "✅";
                case "SESSION_ENDED"   -> "🏁";
                default                -> "📩";
            };

            // Layout
            Label lblIcon    = new Label(icon);
            lblIcon.setStyle("-fx-font-size: 18px; -fx-min-width: 28px;");

            Label lblContent = new Label(content);
            lblContent.setWrapText(true);
            lblContent.setMaxWidth(280);
            lblContent.setStyle("-fx-font-size: 12px; -fx-text-fill: "
                    + (isRead ? "#94a3b8" : "white") + ";");

            Label lblTime = new Label(time);
            lblTime.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");

            VBox textBox = new VBox(2, lblContent, lblTime);
            HBox cell    = new HBox(10, lblIcon, textBox);
            cell.setStyle("-fx-padding: 10 12 10 12;");

            // Màu nền: chưa đọc = nổi bật hơn
            String bg = isRead
                    ? "-fx-background-color: transparent;"
                    : "-fx-background-color: rgba(99,102,241,0.12); "
                    + "-fx-border-color: transparent transparent #6366f1 transparent; "
                    + "-fx-border-width: 0 0 1 0;";
            cell.setStyle(cell.getStyle() + bg);

            setGraphic(cell);
            setText(null);
            setStyle("-fx-background-color: transparent; -fx-padding: 0;");
        }
    }
}
