package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.client.viewmodel.AuctionRow;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Controller màn hình quản trị viên.
 *
 * <p>Admin có thể:
 * <ul>
 *   <li>Xem danh sách toàn bộ phiên đấu giá (LIST_DETAIL)</li>
 *   <li>Xem thống kê: tổng phiên, đang chạy, đã kết thúc</li>
 *   <li>Đóng cưỡng bức bất kỳ phiên nào (CLOSE_SESSION)</li>
 * </ul>
 * </p>
 */
public class AdminDashboardController {

    @FXML private Label               lblWelcome;
    @FXML private TableView<AuctionRow> auctionTable;
    @FXML private Label               lblTotalSessions;
    @FXML private Label               lblRunning;
    @FXML private Label               lblFinished;

    private final ObservableList<AuctionRow> auctionData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        var user = UserManager.getInstance().getCurrentUser();
        if (user != null) {
            lblWelcome.setText("Quản trị viên: " + user.getFullName());
        }
        auctionTable.setItems(auctionData);
        loadDataAsync();
    }

    // -------------------------------------------------------------------------
    // Load dữ liệu — dùng LIST_DETAIL (1 lần call)
    // -------------------------------------------------------------------------
    private void loadDataAsync() {
        new Thread(() -> {
            String response = NetworkClient.getInstance().sendRequest("LIST_DETAIL");
            Platform.runLater(() -> {
                auctionData.clear();
                if (response == null || !response.startsWith("DANH_SACH_CHI_TIET")) return;

                String[] entries = response.split("\\|");
                int stt = 1, running = 0, finished = 0;

                for (int i = 1; i < entries.length; i++) {
                    if ("trong".equalsIgnoreCase(entries[i])) break;
                    // Format: id;itemName;price;status;startTime;endTime
                    String[] p = entries[i].split(";");
                    if (p.length < 4) continue;

                    String status       = p[3];
                    String startTimeStr = p.length > 4 ? p[4] : "";
                    String endTimeStr   = p.length > 5 ? p[5] : "";

                    if ("RUNNING".equals(status) || "EXTENDED".equals(status)) running++;
                    if ("FINISHED".equals(status) || "SUCCESS".equals(status) || "FAILED".equals(status) || "CANCELED".equals(status) || "PAID".equals(status)) finished++;

                    double price         = parseDouble(p[2]);
                    String timeRemaining = formatTimeRemaining(status, startTimeStr, endTimeStr);
                    auctionData.add(new AuctionRow(stt++, p[0], p[1], price, 0, status,
                            startTimeStr, endTimeStr, timeRemaining));
                }

                if (lblTotalSessions != null) lblTotalSessions.setText(String.valueOf(auctionData.size()));
                if (lblRunning       != null) lblRunning.setText(String.valueOf(running));
                if (lblFinished      != null) lblFinished.setText(String.valueOf(finished));
            });
        }).start();
    }

    // -------------------------------------------------------------------------
    // Event Handlers
    // -------------------------------------------------------------------------

    @FXML
    void handleForceClose(ActionEvent event) {
        AuctionRow selected = auctionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn phiên",
                    "Vui lòng chọn một phiên đấu giá để đóng.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Đóng phiên [" + selected.getAuctionId() + "] — " + selected.getItemName() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận đóng phiên");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                String response = NetworkClient.getInstance()
                        .sendRequest("CLOSE_SESSION|" + selected.getAuctionId());
                if (response != null && response.startsWith("CLOSE_SESSION_SUCCESS")) {
                    showAlert(Alert.AlertType.INFORMATION, "Thành công",
                            "Đã đóng phiên: " + selected.getAuctionId());
                    loadDataAsync();
                } else {
                    String msg = (response != null && response.contains("|"))
                            ? response.split("\\|", 2)[1] : "Không thể đóng phiên.";
                    showAlert(Alert.AlertType.ERROR, "Lỗi", msg);
                }
            }
        });
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadDataAsync();
    }

    @FXML
    void handleLogout(ActionEvent event) {
        UserManager.getInstance().logout();
        SceneUtil.changeScene(event, "Login.fxml", "Đăng nhập");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private String formatTimeRemaining(String status, String startTimeStr, String endTimeStr) {
        try {
            if ("PENDING".equalsIgnoreCase(status)) {
                if (startTimeStr == null || startTimeStr.isBlank()) return "00:00:00";
                long secs = Duration.between(LocalDateTime.now(), LocalDateTime.parse(startTimeStr)).getSeconds();
                if (secs <= 0) return "00:00:00";
                return String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
            } else {
                if (endTimeStr == null || endTimeStr.isBlank()) return "00:00:00";
                long secs = Duration.between(LocalDateTime.now(), LocalDateTime.parse(endTimeStr)).getSeconds();
                if (secs <= 0) return "Đã kết thúc";
                return String.format("%02d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60);
            }
        } catch (Exception e) { return "00:00:00"; }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
