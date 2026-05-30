package com.auction.client.controller;

import com.auction.common.models.Bidder;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class RegisterController {

    @FXML
    private TextField txtUser;

    @FXML
    private TextField txtEmail;

    @FXML
    private TextField txtAddress;

    @FXML
    private PasswordField txtPass;

    @FXML
    private Label lblError;

    @FXML
    private ComboBox<String> cbRole;

    @FXML
    public void initialize() {
        cbRole.getItems().addAll("Người mua (Bidder)", "Người bán (Seller)");
        cbRole.setValue("Người mua (Bidder)");
    }

    @FXML
    void handleRegister(ActionEvent event) {
        String username = txtUser.getText().trim();
        String password = txtPass.getText().trim();
        String email = txtEmail.getText().trim();
        String role = cbRole.getValue();

        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            lblError.setText("Vui lòng nhập đầy đủ thông tin bắt buộc!");
            return;
        }

        // Kiểm tra email hợp lệ bằng Regex
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        if (!email.matches(emailRegex)) {
            lblError.setText("Email không hợp lệ (VD: user@gmail.com)");
            return;
        }

        com.auction.client.service.NetworkClient client = com.auction.client.service.NetworkClient.getInstance();
        if (!client.connect("localhost", 9999)) {
            lblError.setText("Lỗi kết nối Server! Máy chủ có thể chưa chạy.");
            return;
        }

        String roleCode;
        if (role.contains("Seller")) {
            roleCode = "SELLER";
        } else {
            roleCode = "BIDDER";
        }
        String request = "REGISTER|" + username + "|" + password + "|" + email + "|" + roleCode;
        String response = client.sendRequest(request);

        if (response.startsWith("REGISTER_SUCCESS")) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Đăng ký thành công! Vui lòng đăng nhập.");
            alert.showAndWait();
            SceneUtil.changeScene(event, "Login.fxml", "Đăng nhập");
        } else if (response.startsWith("REGISTER_FAILED")) {
            String[] parts = response.split("\\|");
            lblError.setText(parts.length > 1 ? parts[1] : "Đăng ký thất bại!");
        } else {
            lblError.setText("Lỗi hệ thống: " + response);
        }
    }

    @FXML
    void handleLoginRedirect(ActionEvent event) {
        SceneUtil.changeScene(event, "Login.fxml", "Dang nhap");
    }
}
