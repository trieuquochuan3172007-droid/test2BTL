package com.auction.client.controller;

import com.auction.client.service.NetworkClient;
import com.auction.common.util.SceneUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class ActivityLogController {

    @FXML private TableView<String[]>           logTable;
    @FXML private TableColumn<String[], String> colUser;
    @FXML private TableColumn<String[], String> colAction;
    @FXML private TableColumn<String[], String> colDetail;
    @FXML private TableColumn<String[], String> colTime;

    private final ObservableList<String[]> logData =
            FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colUser.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()[0]));
        colAction.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()[1]));
        colDetail.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()[2]));
        colTime.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue()[3]));
        logTable.setItems(logData);
        loadLogs();
    }

    private void loadLogs() {
        new Thread(() -> {
            String res = NetworkClient.getInstance()
                    .sendRequest("GET_ACTIVITY_LOG");
            Platform.runLater(() -> {
                logData.clear();
                if (res == null || "ACTIVITY_LOG|trong".equals(res)) return;
                String[] entries = res.split("\\|");
                for (int i = 1; i < entries.length; i++) {
                    String[] parts = entries[i].split(";", 4);
                    if (parts.length >= 4) logData.add(parts);
                }
            });
        }).start();
    }

    @FXML
    void handleRefresh(ActionEvent event) {
        loadLogs();
    }

    @FXML
    void handleBack(ActionEvent event) {
        SceneUtil.changeScene(event, "MainAuction.fxml", "San dau gia");
    }
}