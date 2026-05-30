package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.client.viewmodel.AuctionRow;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Controller màn hình quản lý sản phẩm (phiên đấu giá) của Seller.
 *
 * <p>Seller có thể:
 * <ul>
 *   <li>Xem danh sách các phiên đấu giá mình đã tạo</li>
 *   <li>Sửa tên sản phẩm (chỉ khi chưa có bid)</li>
 *   <li>Xóa phiên đấu giá (chỉ khi chưa có bid)</li>
 * </ul>
 * </p>
 */
public class SellerProductsController {

    @FXML private Label               lblWelcome;
    @FXML private TableView<AuctionRow> productTable;
    @FXML private TableColumn<AuctionRow, Integer> colStt;
    @FXML private TableColumn<AuctionRow, String>  colAuctionId;
    @FXML private TableColumn<AuctionRow, String>  colItemName;
    @FXML private TableColumn<AuctionRow, Double>  colCurrentPrice;
    @FXML private TableColumn<AuctionRow, String>  colStatus;
    @FXML private TableColumn<AuctionRow, String>  colTimeRemaining;
    @FXML private Label               lblTotal;

    private final ObservableList<AuctionRow> productData = FXCollections.observableArrayList();
    private String sellerId;

    @FXML
    public void initialize() {
        User user = UserManager.getInstance().getCurrentUser();
        if (user != null) {
            sellerId = user.getId();
            lblWelcome.setText("Quản lý sản phẩm — " + user.getFullName());
        }

        // Ánh xạ cột
        colStt.setCellValueFactory(c -> c.getValue().sttProperty().asObject());
        colAuctionId.setCellValueFactory(c -> c.getValue().auctionIdProperty());
        colItemName.setCellValueFactory(c -> c.getValue().itemNameProperty());
        colCurrentPrice.setCellValueFactory(c -> c.getValue().currentPriceProperty().asObject());
        colStatus.setCellValueFactory(c -> c.getValue().statusProperty());
        colTimeRemaining.setCellValueFactory(c -> c.getValue().timeRemainingProperty());

        // Custom cell factories
        setupCellFactories();

        productTable.setItems(productData);
        loadMyAuctionsAsync();
    }

    // -------------------------------------------------------------------------
    // Cell Factories
    // -------------------------------------------------------------------------
    private void setupCellFactories() {
        colStt.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%02d", item));
                setStyle("-fx-text-fill: #94a3b8; -fx-alignment: center; -fx-font-weight: bold;");
            }
        });

        colAuctionId.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "#AUC-" + item);
                setStyle("-fx-text-fill: #94a3b8; -fx-font-weight: bold;");
            }
        });

        colItemName.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText("📦 " + item);
                setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
            }
        });

        colCurrentPrice.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("%,.0f ₫", item));
                setStyle("-fx-text-fill: #10b981; -fx-font-weight: 800;");
            }
        });

        colStatus.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { badge.setPadding(new Insets(4, 10, 4, 10));
              badge.setStyle("-fx-background-radius: 12; -fx-font-weight: bold; -fx-font-size: 11px;"); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); return; }
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

                if ("RUNNING".equalsIgnoreCase(item) || "EXTENDED".equalsIgnoreCase(item)) {
                    badge.setStyle("-fx-background-color: rgba(16,185,129,0.15); -fx-text-fill: #34d399; -fx-background-radius:12; -fx-font-weight:bold; -fx-font-size:11px;");
                } else if ("FINISHED".equalsIgnoreCase(item) || "SUCCESS".equalsIgnoreCase(item) || "FAILED".equalsIgnoreCase(item) || "CANCELED".equalsIgnoreCase(item) || "PAID".equalsIgnoreCase(item)) {
                    badge.setStyle("-fx-background-color: rgba(239,68,68,0.15); -fx-text-fill: #f87171; -fx-background-radius:12; -fx-font-weight:bold; -fx-font-size:11px;");
                } else {
                    badge.setStyle("-fx-background-color: rgba(59,130,246,0.15); -fx-text-fill: #60a5fa; -fx-background-radius:12; -fx-font-weight:bold; -fx-font-size:11px;");
                }
                setGraphic(badge);
            }
        });

        colTimeRemaining.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("Đã kết thúc".equals(item)
                    ? "-fx-text-fill: #ef4444; -fx-font-family: 'Courier New'; -fx-font-weight: bold;"
                    : "-fx-text-fill: #f97316; -fx-font-family: 'Courier New'; -fx-font-weight: bold;");
            }
        });
    }

    // -------------------------------------------------------------------------
    // Load dữ liệu
    // -------------------------------------------------------------------------
    private void loadMyAuctionsAsync() {
        new Thread(() -> {
            String response = NetworkClient.getInstance()
                    .sendRequest("GET_MY_AUCTIONS|" + sellerId);
            Platform.runLater(() -> {
                productData.clear();
                if (response == null || !response.startsWith("MY_AUCTIONS")) return;
                String[] entries = response.split("\\|");
                int stt = 1;
                for (int i = 1; i < entries.length; i++) {
                    if ("trong".equalsIgnoreCase(entries[i])) break;
                    // Format: id;itemName;price;status;startTime;endTime
                    String[] p = entries[i].split(";");
                    if (p.length < 4) continue;
                    double price        = parseDouble(p[2]);
                    String status       = p[3];
                    String startTimeStr = p.length > 4 ? p[4] : "";
                    String endTimeStr   = p.length > 5 ? p[5] : "";
                    String remaining    = formatRemaining(status, startTimeStr, endTimeStr);
                    productData.add(new AuctionRow(stt++, p[0], p[1], price, 0, status,
                            startTimeStr, endTimeStr, remaining));
                }
                if (lblTotal != null) lblTotal.setText(String.valueOf(productData.size()));
            });
        }).start();
    }

    // -------------------------------------------------------------------------
    // Event Handlers
    // -------------------------------------------------------------------------

    /** Sửa tên sản phẩm của phiên được chọn. */
    @FXML
    void handleEdit(ActionEvent event) {
        AuctionRow selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn", "Vui lòng chọn một phiên để sửa.");
            return;
        }

        if (!"PENDING".equalsIgnoreCase(selected.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Không thể sửa", "Chỉ có thể sửa tên sản phẩm khi phiên đấu giá chưa bắt đầu (trạng thái: Đang chờ / PENDING).");
            return;
        }

        // Dialog nhập tên mới
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Sửa tên sản phẩm");
        dialog.setHeaderText("Phiên: #AUC-" + selected.getAuctionId());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField txtName = new TextField(selected.getItemName());
        txtName.setPrefWidth(300);
        grid.add(new Label("Tên sản phẩm mới:"), 0, 0);
        grid.add(txtName, 1, 0);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> btn == ButtonType.OK ? txtName.getText().trim() : null);
        Optional<String> result = dialog.showAndWait();

        result.filter(name -> !name.isEmpty()).ifPresent(newName -> {
            String request = "UPDATE_AUCTION|" + selected.getAuctionId()
                           + "|" + sellerId + "|" + newName;
            String response = NetworkClient.getInstance().sendRequest(request);

            if (response != null && response.startsWith("UPDATE_AUCTION_SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Đã cập nhật tên sản phẩm thành: " + newName);
                loadMyAuctionsAsync();
            } else {
                String msg = extractError(response);
                showAlert(Alert.AlertType.ERROR, "Lỗi cập nhật", msg);
            }
        });
    }

    /** Xóa phiên đấu giá được chọn. */
    @FXML
    void handleDelete(ActionEvent event) {
        AuctionRow selected = productTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn", "Vui lòng chọn một phiên để xóa.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Bạn có chắc muốn xóa phiên [" + selected.getAuctionId()
                + "] — " + selected.getItemName() + "?\n\n"
                + "⚠ Lưu ý: Hệ thống sẽ tự động hoàn trả toàn bộ tiền đang tạm khóa cho người đấu giá dẫn đầu (nếu có).",
                ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Xác nhận xóa phiên");
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                String request  = "DELETE_AUCTION|" + selected.getAuctionId() + "|" + sellerId;
                String response = NetworkClient.getInstance().sendRequest(request);

                if (response != null && response.startsWith("DELETE_AUCTION_SUCCESS")) {
                    showAlert(Alert.AlertType.INFORMATION, "Đã xóa",
                            "Đã xóa phiên: " + selected.getAuctionId());
                    loadMyAuctionsAsync();
                } else {
                    String msg = extractError(response);
                    showAlert(Alert.AlertType.ERROR, "Không thể xóa", msg);
                }
            }
        });
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadMyAuctionsAsync();
    }

    @FXML
    void handleBack(ActionEvent event) {
        SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }

    private String formatRemaining(String status, String startTimeStr, String endTimeStr) {
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

    private String extractError(String response) {
        if (response == null) return "Không nhận được phản hồi từ server.";
        if (response.contains("|")) return response.split("\\|", 2)[1];
        return response;
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
