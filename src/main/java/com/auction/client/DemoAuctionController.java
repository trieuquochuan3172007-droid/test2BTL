package com.auction.client;

import com.auction.domain.AuctionSession;
import com.auction.domain.AuctionStatus;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

// Cầu nối FXML ↔ domain: gọi AuctionSession, cập nhật control.
public class DemoAuctionController {

    private final AuctionSession session = new AuctionSession("001", "LAPTOP", "SELLER1", 500.0, java.time.LocalDateTime.now(), java.time.LocalDateTime.now().plusHours(1));

    @FXML
    private Label lblPrice;
    @FXML
    private Label lblWinner;
    @FXML
    private Label lblStatus;
    @FXML
    private TextField txtBidderId;
    @FXML
    private TextField txtAmount;
    @FXML
    private Button btnBid;

    @FXML
    private void initialize() {
        session.setStatus(AuctionStatus.RUNNING);
        refreshUi();
    }

    @FXML
    private void onBid() {
        String bidder = txtBidderId.getText().trim();
        double amount;
        try {
            amount = Double.parseDouble(txtAmount.getText().trim());
        } catch (NumberFormatException e) {
            return;
        }
        session.processBid(bidder, amount);
        refreshUi();
    }

    private void refreshUi() {
        lblPrice.setText(String.format("%.2f", session.getCurrentHighestBid()));
        lblWinner.setText(session.getWinnerID());
        lblStatus.setText(session.getStatus().name());
    }
}
