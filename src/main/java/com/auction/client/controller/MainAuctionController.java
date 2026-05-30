package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.client.viewmodel.AuctionRow;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import com.auction.client.controller.NotificationController;
import java.util.function.Consumer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;

import javafx.scene.control.ButtonBar;
import javafx.scene.control.TextInputDialog;
/**
 * Controller màn hình danh sách phiên đấu giá.
 *
 * <p>Dùng lệnh <code>LIST_DETAIL</code> để lấy toàn bộ thông tin phiên
 * trong <strong>một lần gọi mạng duy nhất</strong>, thay vì N+1 calls như trước.</p>
 *
 * <p>Đồng hồ đếm ngược trong bảng được cập nhật <strong>mỗi giây</strong>
 * qua một Timer daemon — không cần reload lại từ server.</p>
 */
public class MainAuctionController {

    @FXML private Label               lblWelcome;
    @FXML private TableView<AuctionRow> auctionTable;
    @FXML private Button              btnCreateAuction;
    @FXML private Button              btnMyProducts;
    @FXML private Button              btnRefresh;

    // Các trường FXML mới cho thống kê & tìm kiếm
    @FXML private TextField           txtSearch;
    @FXML private Label               lblLiveCount;
    @FXML private Label               lblPaginationInfo;

    // Các cột TableView
    @FXML private TableColumn<AuctionRow, Integer> colStt;
    @FXML private TableColumn<AuctionRow, String>  colAuctionId;
    @FXML private TableColumn<AuctionRow, String>  colItemName;
    @FXML private TableColumn<AuctionRow, Double>  colCurrentPrice;
    @FXML private TableColumn<AuctionRow, Integer> colParticipants;
    @FXML private TableColumn<AuctionRow, String>  colStatus;
    @FXML private TableColumn<AuctionRow, String>  colTimeRemaining;

    @FXML private Button btnSellerCenter;
    @FXML private Button btnDashboard;
    @FXML private Button btnActiveAuctions;

    @FXML private Label                  lblBadge;
    @FXML private NotificationController notificationPanelController;
    @FXML private VBox                   notificationPopup;

    @FXML private Button btnBanUser;
    @FXML private Button btnActivityLog;

    private final ObservableList<AuctionRow> auctionData = FXCollections.observableArrayList();
    private AuctionRow selectedAuction;

    /** Timer cập nhật cột "Thời gian còn lại" mỗi giây. */
    private Timer liveCountdownTimer;

    @FXML
    public void initialize() {
        try {
            User currentUser = UserManager.getInstance().getCurrentUser();
            if (currentUser != null) {
                lblWelcome.setText("Xin chào, " + currentUser.getFullName()
                        + " | Vai trò: " + currentUser.getClass().getSimpleName());

                // Ẩn/hiện nút theo vai trò
                boolean isSeller = "Seller".equalsIgnoreCase(currentUser.getClass().getSimpleName());
                btnCreateAuction.setVisible(isSeller);
                btnCreateAuction.setManaged(isSeller);
            if (notificationPopup != null) {
                notificationPopup.setVisible(false);
                notificationPopup.setManaged(false);
            }

            // 1. Ánh xạ các thuộc tính vào cột TableColumn
            NetworkClient.getInstance().setNotificationListener(message ->
                Platform.runLater(() -> {
                    if (notificationPanelController != null) {
                        notificationPanelController.onRealtimeNotification(message);
                        updateBellBadge(notificationPanelController.getUnreadCount());
                    }
                })
            );
            if (notificationPopup != null) {
                notificationPopup.setVisible(false);
                notificationPopup.setManaged(false);
            }
        }

            // 1. Ánh xạ các thuộc tính vào cột TableColumn
            colStt.setCellValueFactory(cellData -> cellData.getValue().sttProperty().asObject());
            colAuctionId.setCellValueFactory(cellData -> cellData.getValue().auctionIdProperty());
            colItemName.setCellValueFactory(cellData -> cellData.getValue().itemNameProperty());
            colCurrentPrice.setCellValueFactory(cellData -> cellData.getValue().currentPriceProperty().asObject());
            colParticipants.setCellValueFactory(cellData -> cellData.getValue().participantCountProperty().asObject());
            colStatus.setCellValueFactory(cellData -> cellData.getValue().statusProperty());
            colTimeRemaining.setCellValueFactory(cellData -> cellData.getValue().timeRemainingProperty());

            // 2. Cài đặt các custom CellFactory tuyệt đẹp
            setupCustomCellFactories();

            // 3. Tích hợp bộ lọc tìm kiếm Realtime (FilteredList)
            FilteredList<AuctionRow> filteredData = new FilteredList<>(auctionData, p -> true);
            txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                filteredData.setPredicate(auction -> {
                    if (newValue == null || newValue.isEmpty()) {
                        return true;
                    }
                    String lowerCaseFilter = newValue.toLowerCase();
                    if (auction.getItemName().toLowerCase().contains(lowerCaseFilter)) {
                        return true;
                    } else if (auction.getAuctionId().toLowerCase().contains(lowerCaseFilter)) {
                        return true;
                    }
                    return false;
                });
                updatePaginationLabel(filteredData.size());
            });

            SortedList<AuctionRow> sortedData = new SortedList<>(filteredData);
            sortedData.comparatorProperty().bind(auctionTable.comparatorProperty());
            auctionTable.setItems(sortedData);

            auctionTable.setOnMouseClicked(e ->
                    selectedAuction = auctionTable.getSelectionModel().getSelectedItem());

            loadAuctionDataAsync();

        } catch (Exception e) {
            System.err.println("[MainController] Initialize lỗi: " + e.getMessage());
        }
        // Ẩn/hiện theo role
        String role = UserManager.getInstance().getCurrentUser().getRole();
        boolean isSeller = "SELLER".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
        boolean isBidder = "BIDDER".equalsIgnoreCase(role);

        btnSellerCenter.setVisible(isSeller || "ADMIN".equalsIgnoreCase(role));
        btnSellerCenter.setManaged(isSeller || "ADMIN".equalsIgnoreCase(role));

        // Ẩn nút tạo phiên và quản lý sản phẩm với Bidder
        btnCreateAuction.setVisible(isSeller);
        btnCreateAuction.setManaged(isSeller);
        btnMyProducts.setVisible(isSeller);
        btnMyProducts.setManaged(isSeller);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        btnBanUser.setVisible(isAdmin);
        btnBanUser.setManaged(isAdmin);
        btnActivityLog.setVisible(isAdmin);
        btnActivityLog.setManaged(isAdmin);
    }

    private void updatePaginationLabel(int filteredSize) {
        lblPaginationInfo.setText("Hiển thị " + filteredSize + " trên " + auctionData.size() + " phiên đang diễn ra");
    }

    /**
     * Khởi tạo và thiết lập các custom CellFactory cho từng cột của TableView
     */
    private void setupCustomCellFactories() {
        // Cột STT: Định dạng hai số dạng 01, 02...
        colStt.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%02d", item));
                    setStyle("-fx-text-fill: #64748b; -fx-alignment: center; -fx-font-weight: bold;");
                }
            }
        });

        // Cột Phiên: Thêm mã ký tự #AUC-
        colAuctionId.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("#AUC-" + item);
                    setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold;");
                }
            }
        });

        // Cột Vật phẩm: Thêm biểu tượng trực quan emoji dựa theo tên sản phẩm
        colItemName.setCellFactory(column -> new TableCell<>() {
            private final HBox container = new HBox(8);
            private final StackPane imgPlaceholder = new StackPane();
            private final Label lblIcon = new Label("📦");
            private final Label lblName = new Label();
            {
                container.setAlignment(Pos.CENTER_LEFT);
                imgPlaceholder.setPrefSize(28, 28);
                imgPlaceholder.setMinSize(28, 28);
                imgPlaceholder.setMaxSize(28, 28);
                imgPlaceholder.setStyle("-fx-background-color: #1e293b; -fx-background-radius: 6; -fx-border-color: #334155; -fx-border-radius: 6;");
                lblIcon.setStyle("-fx-font-size: 14px;");
                imgPlaceholder.getChildren().add(lblIcon);
                lblName.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                container.getChildren().addAll(imgPlaceholder, lblName);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblName.setText(item);
                    String lower = item.toLowerCase();
                    if (lower.contains("đồng hồ") || lower.contains("rolex") || lower.contains("watch")) {
                        lblIcon.setText("⌚");
                    } else if (lower.contains("tranh") || lower.contains("art") || lower.contains("painting")) {
                        lblIcon.setText("🖼️");
                    } else if (lower.contains("sách") || lower.contains("book")) {
                        lblIcon.setText("📖");
                    } else if (lower.contains("laptop") || lower.contains("phone") || lower.contains("computer")) {
                        lblIcon.setText("💻");
                    } else {
                        lblIcon.setText("📦");
                    }
                    setGraphic(container);
                }
            }
        });

        // Cột Giá: Định dạng dấu phẩy phân tách phần nghìn
        colCurrentPrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f", item));
                    setStyle("-fx-text-fill: #10b981; -fx-font-weight: 800;");
                }
            }
        });

        // Cột Số người tham gia: Thêm icon nhóm người
        colParticipants.setCellFactory(column -> new TableCell<>() {
            private final HBox container = new HBox(6);
            private final Label lblIcon = new Label("👥");
            private final Label lblCount = new Label();
            {
                container.setAlignment(Pos.CENTER_LEFT);
                lblIcon.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px;");
                lblCount.setStyle("-fx-text-fill: #cbd5e1; -fx-font-weight: 600;");
                container.getChildren().addAll(lblIcon, lblCount);
            }

            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    lblCount.setText(String.valueOf(item));
                    setGraphic(container);
                }
            }
        });

        // Cột Trạng thái: Dạng badge viên thuốc bo tròn màu sắc
        colStatus.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.setPadding(new Insets(4, 10, 4, 10));
                badge.setStyle("-fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String display = item.toUpperCase();
                    switch (display) {
                        case "SUCCESS" -> display = "THÀNH CÔNG";
                        case "FAILED" -> display = "THẤT BẠI";
                        case "CANCELED" -> display = "ĐÃ HỦY";
                        case "PENDING" -> display = "CHỜ BẮT ĐẦU";
                        case "RUNNING" -> display = "ĐANG DIỄN RA";
                        case "EXTENDED" -> display = "GIA HẠN";
                    }
                    badge.setText("• " + display);

                    if ("RUNNING".equalsIgnoreCase(item) || "EXTENDED".equalsIgnoreCase(item) || "ĐANG DIỄN RA".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-background-color: rgba(16, 185, 129, 0.15); -fx-text-fill: #34d399; -fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else if ("FINISHED".equalsIgnoreCase(item) || "SUCCESS".equalsIgnoreCase(item) || "FAILED".equalsIgnoreCase(item) || "CANCELED".equalsIgnoreCase(item) || "ĐÃ KẾT THÚC".equalsIgnoreCase(item) || "PAID".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.15); -fx-text-fill: #f87171; -fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        badge.setStyle("-fx-background-color: rgba(59, 130, 246, 0.15); -fx-text-fill: #60a5fa; -fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;");
                    }
                    setGraphic(badge);
                }
            }
        });

        // Cột Đồng hồ đếm ngược: Đậm màu cam nổi bật
        colTimeRemaining.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    if ("Đã kết thúc".equals(item)) {
                        setStyle("-fx-text-fill: #ef4444; -fx-font-family: 'Courier New'; -fx-font-weight: bold; -fx-font-size: 14px;");
                    } else {
                        setStyle("-fx-text-fill: #f97316; -fx-font-family: 'Courier New'; -fx-font-weight: bold; -fx-font-size: 14px;");
                    }
                }
            }
        });
    }

    // -------------------------------------------------------------------------
    // Load dữ liệu
    // -------------------------------------------------------------------------

    private void loadAuctionDataAsync() {
        Thread loadThread = new Thread(() -> {
            try {
                loadAuctionData();
            } catch (Exception e) {
                System.err.println("[MainController] Lỗi tải dữ liệu: " + e.getMessage());
                Platform.runLater(() ->
                        showAlert(Alert.AlertType.WARNING, "Lỗi tải dữ liệu",
                                "Không thể tải danh sách phiên: " + e.getMessage()));
            }
        });
        loadThread.setDaemon(true);
        loadThread.start();
    }

    /**
     * Tải danh sách phiên đấu giá từ server bằng <strong>một lần gọi duy nhất</strong> (LIST_DETAIL).
     *
     * <p>Format server trả về:
     * {@code DANH_SACH_CHI_TIET|id:itemName:price:status:endTime|...}</p>
     */
    private void loadAuctionData() {
        String response = NetworkClient.getInstance().sendRequest("LIST_DETAIL");

        if (response == null || !response.startsWith("DANH_SACH_CHI_TIET")) {
            Platform.runLater(() ->
                    showAlert(Alert.AlertType.WARNING, "Không tải được dữ liệu",
                            "Không thể lấy danh sách phiên từ máy chủ."));
            return;
        }

        ObservableList<AuctionRow> rows = FXCollections.observableArrayList();
        String[] entries = response.split("\\|");
        int stt = 1;

        for (int i = 1; i < entries.length; i++) {
            if ("trong".equalsIgnoreCase(entries[i])) break;

            // Format: id;itemName;price;status;startTime;endTime
            String[] parts = entries[i].split(";");
            if (parts.length < 6) continue;

            String auctionId     = parts[0];
            String itemName      = parts[1];
            double currentPrice  = parseDouble(parts[2]);
            String status        = parts[3];
            String startTimeStr  = parts[4];
            String endTimeStr    = parts[5];
            int participantCount = parts.length > 6 ? (int) parseDouble(parts[6]) : 0;
            String timeRemaining = calculateTimeRemaining(status, startTimeStr, endTimeStr);

            rows.add(new AuctionRow(stt++, auctionId, itemName, currentPrice,
                    participantCount, status, startTimeStr, endTimeStr, timeRemaining));
        }

        Platform.runLater(() -> {
            auctionData.setAll(rows);
            lblLiveCount.setText(String.valueOf(rows.size()));
            updatePaginationLabel(rows.size());
            startLiveCountdown();   // Khởi động timer đếm ngược realtime
            // Nếu đang filter thì áp lại filter sau khi load
            if (isFilteringActive) filterActiveOnly();
        });
    }
    private boolean isFilteringActive = false;

    private void filterActiveOnly() {
        ObservableList<AuctionRow> filtered = FXCollections.observableArrayList();
        for (AuctionRow row : auctionData) {
            String s = row.getStatus();
            if ("RUNNING".equalsIgnoreCase(s) || "EXTENDED".equalsIgnoreCase(s)) {
                filtered.add(row);
            }
        }
        auctionTable.setItems(filtered);
        lblLiveCount.setText(String.valueOf(filtered.size()));
        updatePaginationLabel(filtered.size());
    }

    // -------------------------------------------------------------------------
    // Live countdown timer — cập nhật cột "Thời gian còn lại" mỗi giây
    // -------------------------------------------------------------------------

    /**
     * Bắt đầu Timer daemon cập nhật cột thời gian còn lại mỗi giây.
     * Huỷ timer cũ (nếu có) trước khi tạo timer mới.
     */
    private void startLiveCountdown() {
        if (liveCountdownTimer != null) {
            liveCountdownTimer.cancel();
        }
        liveCountdownTimer = new Timer("main-auction-countdown", true); // daemon
        liveCountdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    boolean allDone = true;
                    for (AuctionRow row : auctionData) {
                        String display = calculateTimeRemaining(row.getStatus(), row.getStartTimeStr(), row.getEndTimeStr());
                        row.timeRemainingProperty().set(display);

                        if (!"Đã kết thúc".equals(display)) {
                            allDone = false;
                        }
                    }
                    // Nếu tất cả đã kết thúc thì huỷ timer — không cần chạy thêm
                    if (allDone && !auctionData.isEmpty()) {
                        liveCountdownTimer.cancel();
                    }
                });
            }
        }, 1000, 1000);
    }

    // -------------------------------------------------------------------------
    // Event Handlers
    // -------------------------------------------------------------------------

    @FXML
    void handleLogout(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        UserManager.getInstance().setCurrentUser(null);
        SceneUtil.changeScene(event, "Login.fxml", "Đăng nhập");
    }
    
    @FXML
    void handleProfile(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        SceneUtil.changeScene(event, "Profile.fxml", "Hồ sơ cá nhân");
    }

    @FXML
    void handleCreateAuction(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        SceneUtil.changeScene(event, "CreateAuction.fxml", "Tạo phiên đấu giá mới");
    }

    @FXML
    void handleMyProducts(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        SceneUtil.changeScene(event, "SellerProducts.fxml", "Quản lý sản phẩm của tôi");
    }

    @FXML
    void handleJoinAuction(ActionEvent event) {
        if (selectedAuction == null) {
            showAlert(Alert.AlertType.INFORMATION, "Chưa chọn phiên",
                    "Vui lòng chọn một phiên đấu giá để tham gia!");
            return;
        }

        if ("PENDING".equalsIgnoreCase(selectedAuction.getStatus()) || "CHỜ BẮT ĐẦU".equalsIgnoreCase(selectedAuction.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Phiên chưa bắt đầu", "Phiên đấu giá chưa bắt đầu, xin vui lòng quay lại khi phiên đấu giá bắt đầu.");
            return;
        }

        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        AuctionRoomController.setSelectedAuction(
                selectedAuction.getAuctionId(), selectedAuction.getItemName());
        SceneUtil.changeScene(event, "AuctionRoom.fxml",
                "Phòng đấu giá: " + selectedAuction.getItemName());
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        loadAuctionDataAsync();
    }

    @FXML
    void handleDashboard(ActionEvent event) {
        // Reset filter, hiện tất cả phiên
        isFilteringActive = false;
        loadAuctionDataAsync();
        // Đổi style active
        btnDashboard.getStyleClass().add("active");
        btnActiveAuctions.getStyleClass().remove("active");
    }

    @FXML
    void handleActiveAuctions(ActionEvent event) {
        // Lọc chỉ hiện RUNNING và EXTENDED
        isFilteringActive = true;
        filterActiveOnly();
        // Đổi style active
        btnActiveAuctions.getStyleClass().add("active");
        btnDashboard.getStyleClass().remove("active");
    }
    @FXML
    void handleBanUser(ActionEvent event) {
        // Dialog nhập username
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Ban / Unban User");
        dialog.setHeaderText(null);
        dialog.setContentText("Nhập username cần ban/unban:");
        dialog.showAndWait().ifPresent(username -> {
            if (username.isBlank()) return;

            // Hỏi ban hay unban
            Alert choice = new Alert(Alert.AlertType.CONFIRMATION);
            choice.setTitle("Chọn hành động");
            choice.setHeaderText("User: " + username);
            ButtonType btnBan   = new ButtonType("🔨 Ban");
            ButtonType btnUnban = new ButtonType("✅ Unban");
            ButtonType btnCancel = new ButtonType("Hủy", ButtonBar.ButtonData.CANCEL_CLOSE);
            choice.getButtonTypes().setAll(btnBan, btnUnban, btnCancel);
            choice.showAndWait().ifPresent(btn -> {
                if (btn == btnBan || btn == btnUnban) {
                    boolean isBan = btn == btnBan;
                    new Thread(() -> {
                        String res = NetworkClient.getInstance()
                                .sendRequest("BAN_USER|" + username + "|" + isBan);
                        Platform.runLater(() -> {
                            Alert result = new Alert(Alert.AlertType.INFORMATION);
                            result.setHeaderText(null);
                            if (res.startsWith("BAN_SUCCESS")) {
                                result.setContentText("Da ban user: " + username);
                            } else if (res.startsWith("UNBAN_SUCCESS")) {
                                result.setContentText("Da unban user: " + username);
                            } else {
                                result.setContentText("That bai: " + res);
                            }
                            result.show();
                        });
                    }).start();
                }
            });
        });
    }

    @FXML
    void handleActivityLog(ActionEvent event) {
        if (liveCountdownTimer != null) liveCountdownTimer.cancel();
        SceneUtil.changeScene(event, "ActivityLog.fxml", "Activity Log");
    }

    @FXML
    void handleToggleNotification(ActionEvent event) {
        if (notificationPopup == null) return;
        boolean showing = notificationPopup.isVisible();
        notificationPopup.setVisible(!showing);
        notificationPopup.setManaged(!showing);
    }

    private void updateBellBadge(int count) {
        if (lblBadge == null) return;
        if (count > 0) {
            lblBadge.setText(count > 99 ? "99+" : String.valueOf(count));
            lblBadge.setVisible(true);
        } else {
            lblBadge.setVisible(false);
        }
    }
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private double parseDouble(String value) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private String calculateTimeRemaining(String status, String startTimeStr, String endTimeStr) {
        if ("PENDING".equalsIgnoreCase(status) || "CHỜ BẮT ĐẦU".equalsIgnoreCase(status)) {
            if (startTimeStr == null || startTimeStr.isBlank()) return "00:00:00";
            try {
                LocalDateTime startTime = LocalDateTime.parse(startTimeStr);
                long seconds = Duration.between(LocalDateTime.now(), startTime).getSeconds();
                if (seconds <= 0) return "00:00:00";
                return String.format("%02d:%02d:%02d",
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            } catch (Exception e) {
                return "00:00:00";
            }
        } else {
            if (endTimeStr == null || endTimeStr.isBlank()) return "00:00:00";
            try {
                LocalDateTime endTime = LocalDateTime.parse(endTimeStr);
                long seconds = Duration.between(LocalDateTime.now(), endTime).getSeconds();
                if (seconds <= 0) return "Đã kết thúc";
                return String.format("%02d:%02d:%02d",
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            } catch (Exception e) {
                return "00:00:00";
            }
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
