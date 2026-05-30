package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.Admin;
import com.auction.common.models.Bidder;
import com.auction.common.models.Seller;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controller màn hình đăng nhập.
 *
 * <p>Xác thực được thực hiện hoàn toàn ở phía Server (BCrypt verify).
 * Client chỉ gửi username + password thô qua TCP; server trả về kết quả.</p>
 */
public class LoginController {

    @FXML private TextField     txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Label         lblMessage;
    @FXML private TextField  txtPasswordVisible;
    @FXML private javafx.scene.shape.SVGPath eyeIcon;
    private boolean passwordVisible = false;

    @FXML
    void handleLogin(ActionEvent event) {
        String username = txtUsername.getText().trim();
        String password = (passwordVisible ? txtPasswordVisible : txtPassword).getText().trim();

        // Validation cơ bản phía client
        if (username.isEmpty() || password.isEmpty()) {
            lblMessage.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!");
            return;
        }

        // Kết nối server
        NetworkClient client = NetworkClient.getInstance();
        if (!client.connect("localhost", 9999)) {
            lblMessage.setText("❌ Không thể kết nối đến Máy chủ! Hãy kiểm tra Server.");
            return;
        }

        // Gửi yêu cầu đăng nhập
        String response = client.sendRequest("LOGIN|" + username + "|" + password);
        if (response == null) {
            lblMessage.setText("❌ Không nhận được phản hồi từ Server.");
            return;
        }

        if (response.startsWith("LOGIN_SUCCESS")) {
            // Format: LOGIN_SUCCESS|ROLE|ID|FULLNAME|EMAIL
            String[] parts = response.split("\\|");
            if (parts.length < 5) {
                lblMessage.setText("❌ Phản hồi không hợp lệ từ Server.");
                return;
            }
            String role     = parts[1];
            String id       = parts[2];
            String fullName = parts[3];
            String email    = parts[4];

            // Tạo đối tượng User theo role — Polymorphism
            User loggedInUser = switch (role.toUpperCase()) {
                case "ADMIN"  -> new Admin(id, username, "", fullName, email);
                case "SELLER" -> new Seller(id, username, "", fullName, email);
                default       -> new Bidder(id, username, "", fullName, email, 0.0);
            };

            UserManager.getInstance().setCurrentUser(loggedInUser);

            // Điều hướng theo role
            if ("ADMIN".equalsIgnoreCase(role)) {
                SceneUtil.changeScene(event, "AdminDashboard.fxml", "Quản trị viên");
            } else {
                SceneUtil.changeScene(event, "MainAuction.fxml", "Sàn Đấu Giá");
            }
        } else {
            // Hiển thị lý do lỗi từ server
            String[] parts = response.split("\\|", 2);
            String reason  = (parts.length > 1) ? parts[1] : "Đăng nhập thất bại!";
            lblMessage.setText("❌ " + reason);
        }
    }

    @FXML
    void goToRegister(ActionEvent event) {
        SceneUtil.changeScene(event, "Register.fxml", "Đăng ký tài khoản");
    }
    @FXML
    void handleTogglePassword(javafx.scene.input.MouseEvent event) {
        passwordVisible = !passwordVisible;
        if (passwordVisible) {
            txtPasswordVisible.setText(txtPassword.getText());
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
        } else {
            txtPassword.setText(txtPasswordVisible.getText());
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
        }
    }

    @FXML
    void handleForgotPassword(ActionEvent event) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("Quên mật khẩu");
        alert.setHeaderText(null);
        alert.setContentText("Vui long lien he admin de duoc ho tro lay lai mat khau.\nEmail: admin@auction.com");
        alert.show();
    }
}
