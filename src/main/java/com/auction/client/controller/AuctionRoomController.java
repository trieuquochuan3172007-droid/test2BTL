package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Controller màn hình phòng đấu giá.
 *
 * <p>Tính năng:
 * <ul>
 *   <li>Realtime update qua Observer (NetworkClient.listener)</li>
 *   <li>Đồng hồ đếm ngược</li>
 *   <li>Biểu đồ lịch sử giá (LineChart)</li>
 *   <li>Auto-bid với mức giá tối đa và bước giá tuỳ chỉnh</li>
 * </ul>
 */
public class AuctionRoomController {

    // -------------------------------------------------------------------------
    // Hằng số Auto-bid
    // -------------------------------------------------------------------------
    /** Bước tăng mặc định khi người dùng chưa nhập increment. */
    private static final double DEFAULT_AUTO_BID_INCREMENT = 100_000.0;

    // -------------------------------------------------------------------------
    // Static state truyền giữa màn hình (set từ MainAuctionController)
    // -------------------------------------------------------------------------
    private static String selectedAuctionId;
    private static String selectedAuctionItem;

    // -------------------------------------------------------------------------
    // FXML components
    // -------------------------------------------------------------------------
    @FXML private Label  lblItemName;
    @FXML private Label  lblDescription;
    @FXML private Label  lblCurrentPrice;
    @FXML private Label  lblTimer;
    @FXML private Label  lblWinnerTitle;
    @FXML private Label  lblWinner;
    @FXML private TextField txtBidAmount;
    @FXML private TextField txtAutoBidMax;
    @FXML private TextField txtAutoBidIncrement;
    @FXML private ToggleButton toggleAutoBid;
    @FXML private LineChart<String, Number> bidChart;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private XYChart.Series<String, Number> chartSeries;
    private double currentPrice     = 0;
    private int    secondsRemaining = 3600;
    private Timer  countdownTimer;
    private String currentStatus    = "OPEN";

    private boolean isAutoBidEnabled   = false;
    private double  maxAutoBidAmount   = 0.0;
    private double  autoBidIncrement   = DEFAULT_AUTO_BID_INCREMENT;

    // -------------------------------------------------------------------------
    // Static setter (gọi từ MainAuctionController trước khi chuyển scene)
    // -------------------------------------------------------------------------
    public static void setSelectedAuction(String auctionId, String auctionItem) {
        selectedAuctionId   = auctionId;
        selectedAuctionItem = auctionItem;
    }

    // -------------------------------------------------------------------------
    // Khởi tạo
    // -------------------------------------------------------------------------
    @FXML
    public void initialize() {
        // Thiết lập biểu đồ lịch sử giá (Bid History Visualization)
        chartSeries = new XYChart.Series<>();
        chartSeries.setName("Lịch sử giá");
        bidChart.getData().add(chartSeries);

        // Đăng ký Observer nhận cập nhật realtime từ server
        NetworkClient.getInstance().setListener(this::handleServerMessage);
        NetworkClient.getInstance().startListening();

        // Hiển thị tên phiên
        String itemName = (selectedAuctionItem != null) ? selectedAuctionItem : "Đang tải...";
        lblItemName.setText(itemName);

        // Tải dữ liệu phiên từ server
        if (selectedAuctionId != null) {
            loadAuctionDataFromServer(selectedAuctionId);
        } else {
            startCountdown();
        }
    }

    // -------------------------------------------------------------------------
    // Realtime Observer — nhận broadcast từ server
    // -------------------------------------------------------------------------

    /**
     * Xử lý tin nhắn broadcast từ server (Observer Pattern).
     * Gọi trên background thread → dùng Platform.runLater để cập nhật UI.
     *
     * <p>Format: {@code CAP_NHAT|id=...|gia_hien_tai=...|nguoi_dan_dau=...|trang_thai=...|end_time=...}</p>
     */
    private void handleServerMessage(String message) {
        String[] parts = message.split("\\|");
        if (!"CAP_NHAT".equals(parts[0])) return;

        Map<String, String> data = parseKeyValue(message);
        String auctionId = data.getOrDefault("id", "");

        // Chỉ xử lý broadcast của phiên đang xem
        if (!auctionId.equals(selectedAuctionId)) return;

        Platform.runLater(() -> {
            double newPrice   = parseDouble(data.getOrDefault("gia_hien_tai", "0"), currentPrice);
            String newWinner  = data.getOrDefault("nguoi_dan_dau", "");
            String newStatus  = data.getOrDefault("trang_thai", currentStatus);
            String endTimeStr = data.getOrDefault("end_time", "");

            if (newPrice > currentPrice) {
                currentPrice = newPrice;
                updatePriceDisplay(currentPrice, newWinner.isBlank() ? "Chưa có" : newWinner);
                addChartPoint(currentPrice);
            }

            // Cập nhật bộ đếm nếu thời gian được gia hạn (Anti-sniping)
            if (!endTimeStr.isBlank()) {
                int remaining = parseSecondsUntil(endTimeStr, secondsRemaining);
                if (remaining > secondsRemaining) {
                    secondsRemaining = remaining;
                }
            }

            if ("FINISHED".equalsIgnoreCase(newStatus) || "SUCCESS".equalsIgnoreCase(newStatus) || "FAILED".equalsIgnoreCase(newStatus) || "CANCELED".equalsIgnoreCase(newStatus)) {
                onAuctionFinished();
            } else if ("RUNNING".equalsIgnoreCase(newStatus) && ("PENDING".equalsIgnoreCase(currentStatus) || "CHỜ BẮT ĐẦU".equalsIgnoreCase(currentStatus))) {
                int remaining = parseSecondsUntil(endTimeStr, -1);
                secondsRemaining = (remaining >= 0) ? remaining : 3600;
                startCountdown();
            }
            currentStatus = newStatus;

            // Kích hoạt auto-bid nếu có người khác vừa vượt mặt
            checkAndTriggerAutoBid();
        });
    }

    // -------------------------------------------------------------------------
    // Tải dữ liệu ban đầu
    // -------------------------------------------------------------------------
    private void loadAuctionDataFromServer(String auctionId) {
        Thread t = new Thread(() -> {
            String response = NetworkClient.getInstance().sendRequest("GET_SESSION|" + auctionId);
            Platform.runLater(() -> {
                if (response == null || !response.startsWith("PHIEN|")) {
                    showAlert("Lỗi", "Không thể tải dữ liệu phiên từ máy chủ.");
                    // Fallback: dùng 60 phút nếu không lấy được dữ liệu
                    secondsRemaining = 3600;
                    startCountdown();
                    return;
                }
                Map<String, String> data = parseKeyValue(response);
                currentPrice  = parseDouble(data.getOrDefault("gia_hien_tai", "0"), 0);
                String winner = data.getOrDefault("nguoi_dan_dau", "");
                String status = data.getOrDefault("trang_thai", "OPEN");
                currentStatus = status;
                String item   = data.getOrDefault("vat_pham",
                        selectedAuctionItem != null ? selectedAuctionItem : "Sản phẩm");
                String endTimeStr = data.getOrDefault("end_time", "");
                String startTimeStr = data.getOrDefault("start_time", "");

                lblItemName.setText(item);
                lblDescription.setText("Phiên: " + auctionId + " | Sản phẩm: " + item + " | Trạng thái: " + status);
                updatePriceDisplay(currentPrice, winner.isBlank() ? "Chưa có" : winner);
                addChartPoint(currentPrice);

                if ("FINISHED".equalsIgnoreCase(status) || "SUCCESS".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
                    onAuctionFinished();
                } else if ("PENDING".equalsIgnoreCase(status) || "CHỜ BẮT ĐẦU".equalsIgnoreCase(status)) {
                    int remaining = parseSecondsUntil(startTimeStr, -1);
                    secondsRemaining = (remaining >= 0) ? remaining : 3600;
                    startCountdown();
                } else {
                    // Luôn dùng end_time thực từ server — tránh đồng hồ hiển thị 0
                    int remaining = parseSecondsUntil(endTimeStr, -1);
                    secondsRemaining = (remaining >= 0) ? remaining : 3600;
                    startCountdown();
                }
            });
        });
        t.setDaemon(true);
        t.start();
    }

    // -------------------------------------------------------------------------
    // Đồng hồ đếm ngược
    // -------------------------------------------------------------------------
    private void startCountdown() {
        if (countdownTimer != null) countdownTimer.cancel();
        countdownTimer = new Timer(true); // daemon timer
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (secondsRemaining > 0) {
                    secondsRemaining--;
                    Platform.runLater(() -> {
                        lblTimer.setText(formatTime(secondsRemaining));
                        if (secondsRemaining <= 30) {
                            lblTimer.setStyle("-fx-text-fill: #ef4444; -fx-font-size: 28px; -fx-font-weight: bold;");
                        } else if (secondsRemaining <= 300) {
                            lblTimer.setStyle("-fx-text-fill: #f59e0b; -fx-font-size: 28px; -fx-font-weight: bold;");
                        } else {
                            lblTimer.setStyle("-fx-text-fill: #10b981; -fx-font-size: 28px; -fx-font-weight: bold;");
                        }
                    });
                } else {
                    countdownTimer.cancel();
                    if ("PENDING".equalsIgnoreCase(currentStatus) || "CHỜ BẮT ĐẦU".equalsIgnoreCase(currentStatus)) {
                        Platform.runLater(() -> lblTimer.setText("00:00:00"));
                    } else {
                        Platform.runLater(AuctionRoomController.this::onAuctionFinished);
                    }
                }
            }
        }, 1000, 1000);
    }

    // -------------------------------------------------------------------------
    // Đặt giá thủ công
    // -------------------------------------------------------------------------
    @FXML
    void handlePlaceBid(ActionEvent event) {
        if (secondsRemaining <= 0 || selectedAuctionId == null) return;

        double bidAmount;
        try {
            bidAmount = Double.parseDouble(txtBidAmount.getText().trim());
        } catch (NumberFormatException e) {
            showAlert("Lỗi nhập liệu", "Vui lòng nhập số tiền hợp lệ!");
            return;
        }

        if (bidAmount <= currentPrice) {
            showAlert("Giá không hợp lệ",
                    String.format("Phải đặt cao hơn giá hiện tại: %,.0f VNĐ", currentPrice));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận đặt giá");
        confirm.setHeaderText(null);
        confirm.setContentText(String.format("Bạn chắc chắn muốn đặt %,.0f \u20ab không?", bidAmount));
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                submitBid(bidAmount);
            }
        });
        txtBidAmount.clear();
    }

    // -------------------------------------------------------------------------
    // Auto-bid
    // -------------------------------------------------------------------------
    @FXML
    void handleToggleAutoBid(ActionEvent event) {
        if (toggleAutoBid.isSelected()) {
            // Bật auto-bid
            try {
                maxAutoBidAmount = Double.parseDouble(txtAutoBidMax.getText().trim());
                if (maxAutoBidAmount <= currentPrice) {
                    showAlert("Lỗi Auto-bid", "Giá tối đa phải lớn hơn giá hiện tại!");
                    toggleAutoBid.setSelected(false);
                    return;
                }
                // Đọc bước giá tuỳ chỉnh (nếu có)
                String incrementText = (txtAutoBidIncrement != null)
                        ? txtAutoBidIncrement.getText().trim() : "";
                autoBidIncrement = incrementText.isBlank()
                        ? DEFAULT_AUTO_BID_INCREMENT
                        : parseDouble(incrementText, DEFAULT_AUTO_BID_INCREMENT);

                isAutoBidEnabled = true;
                toggleAutoBid.setText("Đang bật Auto-bid ✓");
                toggleAutoBid.setStyle("-fx-background-color: #10b981; -fx-text-fill: white;");
                if (txtAutoBidMax != null) txtAutoBidMax.setDisable(true);
                if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(true);

                checkAndTriggerAutoBid();

            } catch (NumberFormatException e) {
                showAlert("Lỗi nhập liệu", "Giá tối đa Auto-bid phải là số!");
                toggleAutoBid.setSelected(false);
            }
        } else {
            // Tắt auto-bid
            disableAutoBid();
        }
    }

    /**
     * Kiểm tra và kích hoạt auto-bid nếu cần.
     * Gọi sau mỗi lần có bid mới (từ thủ công hoặc broadcast).
     */
    private void checkAndTriggerAutoBid() {
        if (!isAutoBidEnabled || secondsRemaining <= 0 || selectedAuctionId == null) return;

        String currentUsername = UserManager.getInstance().getCurrentUser() != null
                ? UserManager.getInstance().getCurrentUser().getUsername() : "anonymous";
        String currentWinner = lblWinner.getText();

        // Nếu mình đang dẫn đầu → không cần auto-bid
        if (currentUsername.equals(currentWinner)) return;

        double nextBid = currentPrice + autoBidIncrement;
        if (nextBid > maxAutoBidAmount) {
            // Vượt ngưỡng — tắt auto-bid và thông báo
            disableAutoBid();
            showAlert("Auto-bid kết thúc",
                    "Giá hiện tại đã vượt mức tối đa auto-bid của bạn.");
            return;
        }

        submitBid(nextBid);
    }

    /**
     * Gửi một lần đặt giá lên server và xử lý kết quả.
     */
    /**
     * Gửi một lần đặt giá lên server trên background thread.
     * KHÔNG được gọi trực tiếp trên JavaFX Application Thread vì sendRequest
     * chặn socket I/O và tranh chấp với startListening() trên cùng stream.
     */
    private void submitBid(double amount) {
        String userId = UserManager.getInstance().getCurrentUser() != null
                ? UserManager.getInstance().getCurrentUser().getId() : "anonymous";

        Thread bidThread = new Thread(() -> {
            String response = NetworkClient.getInstance().sendRequest(
                    "PLACE_BID|" + selectedAuctionId + "|" + userId + "|" + amount);

            Platform.runLater(() -> {
                if (response == null) {
                    showAlert("Lỗi mạng", "Không nhận được phản hồi từ server.");
                    return;
                }

                if (response.startsWith("CHAP_NHAN|") || response.startsWith("CAP_NHAT|")) {
                    Map<String, String> data = parseKeyValue(response);
                    currentPrice  = parseDouble(data.getOrDefault("gia_hien_tai", String.valueOf(currentPrice)), currentPrice);
                    String winner = data.getOrDefault("nguoi_dan_dau", "");
                    updatePriceDisplay(currentPrice, winner.isBlank() ? "Chưa có" : winner);
                    addChartPoint(currentPrice);

                    // Cập nhật bộ đếm nếu anti-sniping gia hạn thời gian
                    String endTimeStr = data.getOrDefault("end_time", "");
                    if (!endTimeStr.isBlank()) {
                        int remaining = parseSecondsUntil(endTimeStr, -1);
                        if (remaining > 0 && remaining > secondsRemaining) {
                            secondsRemaining = remaining;
                        }
                    }

                    String st = data.getOrDefault("trang_thai", "");
                    if ("FINISHED".equalsIgnoreCase(st) || "SUCCESS".equalsIgnoreCase(st) || "FAILED".equalsIgnoreCase(st) || "CANCELED".equalsIgnoreCase(st)) {
                        onAuctionFinished();
                    }
                } else if (response.startsWith("TU_CHOI|")) {
                    String msg = response.split("\\|", 2)[1];
                    showAlert("Đặt giá bị từ chối", msg);
                } else if (response.startsWith("LOI|")) {
                    String msg = response.split("\\|", 2)[1];
                    showAlert("Lỗi", msg);
                } else {
                    showAlert("Đặt giá thất bại", "Phản hồi không xác định: " + response);
                }
            });
        });
        bidThread.setDaemon(true);
        bidThread.start();
    }

    // -------------------------------------------------------------------------
    // Kết thúc phiên
    // -------------------------------------------------------------------------
    private void onAuctionFinished() {
    if (countdownTimer != null) countdownTimer.cancel();
    secondsRemaining = 0;
    lblTimer.setText("ĐÃ KẾT THÚC");
    lblTimer.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
    if (lblWinnerTitle != null) lblWinnerTitle.setText("🏆 NGƯỜI CHIẾN THẮNG");
    txtBidAmount.setDisable(true);
    disableAutoBid();

    // Hiện banner kết quả cho người dùng hiện tại
    String currentUsername = UserManager.getInstance().getCurrentUser() != null
            ? UserManager.getInstance().getCurrentUser().getUsername() : "";
    String winner = lblWinner.getText();

    Alert result = new Alert(Alert.AlertType.INFORMATION);
    result.setHeaderText(null);
    if (!winner.isBlank() && winner.equals(currentUsername)) {
        result.setTitle("Chúc mừng!");
        result.setContentText("🏆 Bạn đã thắng phiên đấu giá!\nSản phẩm: "
                + lblItemName.getText()
                + "\nGiá thắng: " + String.format("%,.0f \u20ab", currentPrice));
    } else if (!winner.isBlank()) {
        result.setTitle("Phiên đấu giá kết thúc");
        result.setContentText("Phiên đấu giá đã kết thúc.\nNgười thắng: "
                + winner
                + "\nGiá thắng: " + String.format("%,.0f \u20ab", currentPrice));
    } else {
        result.setTitle("Phiên đấu giá kết thúc");
        result.setContentText("Phiên đấu giá đã kết thúc mà không có người thắng.");
    }
    result.show();
}

    private void disableAutoBid() {
        isAutoBidEnabled = false;
        toggleAutoBid.setSelected(false);
        toggleAutoBid.setText("Bật Auto-bid");
        toggleAutoBid.setStyle("");
        if (txtAutoBidMax != null)       txtAutoBidMax.setDisable(false);
        if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(false);
    }

    // -------------------------------------------------------------------------
    // Cập nhật UI
    // -------------------------------------------------------------------------
    private void updatePriceDisplay(double price, String winner) {
        lblCurrentPrice.setText(String.format("%,.0f VNĐ", price));
        lblWinner.setText(winner);
    }

    private void addChartPoint(double price) {
        String timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        chartSeries.getData().add(new XYChart.Data<>(timeStr, price));
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------
    @FXML
    void handleBack(ActionEvent event) {
        if (countdownTimer != null) countdownTimer.cancel();
        SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private Map<String, String> parseKeyValue(String response) {
        Map<String, String> data = new HashMap<>();
        String[] parts = response.split("\\|");
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split("=", 2);
            if (kv.length == 2) data.put(kv[0], kv[1]);
        }
        return data;
    }

    private int parseSecondsUntil(String endTimeStr, int fallback) {
        if (endTimeStr == null || endTimeStr.isBlank()) return fallback;
        try {
            LocalDateTime end = LocalDateTime.parse(endTimeStr);
            long secs = Duration.between(LocalDateTime.now(), end).getSeconds();
            return (int) Math.max(0, secs);
        } catch (Exception e) { return fallback; }
    }

    private double parseDouble(String raw, double fallback) {
        try { return Double.parseDouble(raw); }
        catch (NumberFormatException e) { return fallback; }
    }

    private String formatTime(int totalSeconds) {
        return String.format("%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.show();
    }
}
