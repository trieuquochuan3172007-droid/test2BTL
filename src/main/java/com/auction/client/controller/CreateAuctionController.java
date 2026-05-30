package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class CreateAuctionController {

    @FXML
    private TextField txtItemName;

    @FXML
    private ComboBox<String> cbCategory;

    @FXML
    private TextField txtDescription;

    @FXML
    private TextField txtStartPrice;

    @FXML
    private DatePicker dpStartDate;

    @FXML
    private ComboBox<String> cbStartHour;

    @FXML
    private ComboBox<String> cbStartMinute;

    @FXML
    private DatePicker dpEndDate;

    @FXML
    private ComboBox<String> cbEndHour;

    @FXML
    private ComboBox<String> cbEndMinute;

    @FXML
    public void initialize() {
        cbCategory.getItems().addAll("Điện tử (Electronics)", "Nghệ thuật (Art)", "Phương tiện (Vehicle)", "Thời trang (Fashion)", "Khác");
        cbCategory.getSelectionModel().selectFirst();
        applyButtonCell(cbCategory);

        // Populate hours (00-23)
        for (int i = 0; i < 24; i++) {
            String h = String.format("%02d", i);
            cbStartHour.getItems().add(h);
            cbEndHour.getItems().add(h);
        }

        // Populate minutes (00-59)
        for (int i = 0; i < 60; i++) {
            String m = String.format("%02d", i);
            cbStartMinute.getItems().add(m);
            cbEndMinute.getItems().add(m);
        }

        // Apply white text button cells to all hour/minute combos
        applyButtonCell(cbStartHour);
        applyButtonCell(cbStartMinute);
        applyButtonCell(cbEndHour);
        applyButtonCell(cbEndMinute);

        // Set default values: start time is now, end time is now + 1 hour
        LocalDateTime now = LocalDateTime.now();
        dpStartDate.setValue(now.toLocalDate());
        cbStartHour.setValue(String.format("%02d", now.getHour()));
        cbStartMinute.setValue(String.format("%02d", now.getMinute()));

        LocalDateTime defaultEnd = now.plusHours(1);
        dpEndDate.setValue(defaultEnd.toLocalDate());
        cbEndHour.setValue(String.format("%02d", defaultEnd.getHour()));
        cbEndMinute.setValue(String.format("%02d", defaultEnd.getMinute()));
    }

    /** Tạo ButtonCell hiển thị chữ trắng cho ComboBox trên nền tối. */
    private void applyButtonCell(ComboBox<String> combo) {
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(item);
                }
                setStyle("-fx-text-fill: white; -fx-background-color: transparent; -fx-font-size: 14px; -fx-padding: 0 8 0 8;");
            }
        });
    }

    @FXML
    void handleCreate(ActionEvent event) {
        String name = txtItemName.getText().trim();
        String priceStr = txtStartPrice.getText().trim();

        if (name.isEmpty() || priceStr.isEmpty()
                || dpStartDate.getValue() == null || cbStartHour.getValue() == null || cbStartMinute.getValue() == null
                || dpEndDate.getValue() == null || cbEndHour.getValue() == null || cbEndMinute.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin", "Vui lòng nhập đầy đủ Tên, Giá khởi điểm và Thời gian Bắt đầu / Kết thúc!");
            return;
        }

        try {
            double price = Double.parseDouble(priceStr);

            // Construct start and end LocalDateTimes
            LocalDate startDate = dpStartDate.getValue();
            int startH = Integer.parseInt(cbStartHour.getValue());
            int startM = Integer.parseInt(cbStartMinute.getValue());
            LocalDateTime startDateTime = LocalDateTime.of(startDate, LocalTime.of(startH, startM));

            LocalDate endDate = dpEndDate.getValue();
            int endH = Integer.parseInt(cbEndHour.getValue());
            int endM = Integer.parseInt(cbEndMinute.getValue());
            LocalDateTime endDateTime = LocalDateTime.of(endDate, LocalTime.of(endH, endM));

            // Validate that endTime is after startTime
            if (!endDateTime.isAfter(startDateTime)) {
                showAlert(Alert.AlertType.WARNING, "Thời gian không hợp lệ", "Thời gian Kết thúc phải sau thời gian Bắt đầu!");
                return;
            }
            if (endDateTime.isBefore(LocalDateTime.now())) {
                showAlert(Alert.AlertType.WARNING, "Thời gian không hợp lệ", "Thời gian Kết thúc phải ở tương lai!");
                return;
            }

            // Lấy thông tin người dùng hiện tại
            User currentUser = UserManager.getInstance().getCurrentUser();
            String sellerId = (currentUser != null) ? currentUser.getId() : "UNKNOWN";

            // Tạo ID duy nhất cho phiên và item
            String auctionId = "AUC" + System.currentTimeMillis();
            String itemId    = "ITEM" + System.currentTimeMillis();

            // Gửi request lên Server để tạo phiên đấu giá
            // Định dạng: CREATE_AUCTION|auctionId|itemId|itemName|sellerId|startPrice|startTimeISO|endTimeISO
            String request = "CREATE_AUCTION|" + auctionId + "|" + itemId + "|" + name
                           + "|" + sellerId + "|" + price + "|" + startDateTime + "|" + endDateTime;

            String response = NetworkClient.getInstance().sendRequest(request);

            if (response != null && response.startsWith("CREATE_AUCTION_SUCCESS")) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Đã tạo phiên đấu giá thành công!\nMã phiên: " + auctionId);
                SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
            } else {
                String errMsg = (response != null && response.contains("|"))
                        ? response.split("\\|", 2)[1]
                        : (response != null ? response : "Không nhận được phản hồi từ server");
                showAlert(Alert.AlertType.ERROR, "Lỗi tạo phiên", errMsg);
            }

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Lỗi định dạng", "Giá và Thời gian phải là số hợp lệ!");
        }
    }

    @FXML
    void handleCancel(ActionEvent event) {
        SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
