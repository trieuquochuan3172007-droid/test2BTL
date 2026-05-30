package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.models.Bidder;
import com.auction.common.models.User;
import com.auction.common.models.UserManager;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

public class ProfileController {

    // --- Wallet ---
    @FXML private Label     lblAvatar;
    @FXML private Label     lblUsername;
    @FXML private Label     lblRole;
    @FXML private Label     lblBalance;
    @FXML private Label     lblFrozen;
    @FXML private Label     lblEmail;
    @FXML private Label     lblDepositMsg;
    @FXML private TextField txtDeposit;

    // --- My Bids table ---
    @FXML private TableView<String[]>           myBidsTable;
    @FXML private TableColumn<String[], String> colBidItem;
    @FXML private TableColumn<String[], String> colBidAmount;
    @FXML private TableColumn<String[], String> colBidStatus;

    private final ObservableList<String[]> myBidsData =
            FXCollections.observableArrayList();

    private User currentUser;

    @FXML
    public void initialize() {
        currentUser = UserManager.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String name = currentUser.getUsername();
        lblAvatar.setText(name.substring(0, 1).toUpperCase());
        lblUsername.setText(name);
        lblRole.setText("Vai trò: " + currentUser.getRole());
        lblEmail.setText(currentUser.getEmail());

        if (currentUser instanceof Bidder) {
            loadWalletAsync();
            setupMyBidsTable();
            loadMyBidsAsync();
        } else {
            lblBalance.setText("N/A");
            lblFrozen.setText("N/A");
        }
    }

    // -------------------------------------------------------------------------
    // Wallet
    // -------------------------------------------------------------------------
    private void loadWalletAsync() {
        new Thread(() -> {
            String res = NetworkClient.getInstance()
                    .sendRequest("GET_PROFILE|" + currentUser.getId());
            Platform.runLater(() -> {
                if (res != null && res.startsWith("PROFILE_SUCCESS")) {
                    String[] parts = res.split("\\|");
                    lblBalance.setText(String.format("%,.0f \u20ab", parseDouble(parts[1])));
                    lblFrozen.setText(String.format("%,.0f \u20ab",  parseDouble(parts[2])));
                } else {
                    lblBalance.setText("Loi tai");
                    lblFrozen.setText("Loi tai");
                }
            });
        }).start();
    }

    @FXML
    void handleDeposit(ActionEvent event) {
        double amount;
        try {
            amount = Double.parseDouble(txtDeposit.getText().trim().replace(",", ""));
        } catch (NumberFormatException e) {
            showMsg("Vui long nhap so tien hop le.", false);
            return;
        }
        if (amount <= 0) {
            showMsg("So tien phai lon hon 0.", false);
            return;
        }
        new Thread(() -> {
            String res = NetworkClient.getInstance()
                    .sendRequest("DEPOSIT|" + currentUser.getId() + "|" + amount);
            Platform.runLater(() -> {
                if (res != null && res.startsWith("DEPOSIT_SUCCESS")) {
                    lblBalance.setText(String.format("%,.0f \u20ab",
                            parseDouble(res.split("\\|")[1])));
                    txtDeposit.clear();
                    showMsg("Nap tien thanh cong!", true);
                } else {
                    showMsg("Nap tien that bai: " + res, false);
                }
            });
        }).start();
    }

    // -------------------------------------------------------------------------
    // My Bids
    // -------------------------------------------------------------------------
    private void setupMyBidsTable() {
        colBidItem.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()[1]));
        colBidAmount.setCellValueFactory(c ->
                new SimpleStringProperty(
                        String.format("%,.0f \u20ab",
                                parseDouble(c.getValue()[2]))));
        colBidStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()[3]));
        myBidsTable.setItems(myBidsData);
    }

    private void loadMyBidsAsync() {
        new Thread(() -> {
            String res = NetworkClient.getInstance()
                    .sendRequest("GET_MY_BIDS|" + currentUser.getId());
            Platform.runLater(() -> {
                myBidsData.clear();
                if (res == null || "MY_BIDS|trong".equals(res)) return;
                String[] entries = res.split("\\|");
                for (int i = 1; i < entries.length; i++) {
                    String[] parts = entries[i].split(";");
                    if (parts.length >= 4) myBidsData.add(parts);
                }
            });
        }).start();
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------
    @FXML
    void handleBack(ActionEvent event) {
        User user = UserManager.getInstance().getCurrentUser();
        if (user == null) return;
        String role = user.getRole().toUpperCase();
        if ("ADMIN".equals(role)) {
            SceneUtil.changeScene(event, "AdminDashboard.fxml", "Admin");
        } else {
            SceneUtil.changeScene(event, "MainAuction.fxml", "San dau gia");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private void showMsg(String msg, boolean success) {
        lblDepositMsg.setText(msg);
        lblDepositMsg.setStyle("-fx-font-size: 13px; -fx-text-fill: "
                + (success ? "#10b981" : "#ef4444") + ";");
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); }
        catch (Exception e) { return 0; }
    }
}