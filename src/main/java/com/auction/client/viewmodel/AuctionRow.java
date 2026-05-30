package com.auction.client.viewmodel;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleDoubleProperty;

public class AuctionRow {
    private final SimpleIntegerProperty stt;
    private final SimpleStringProperty auctionId;
    private final SimpleStringProperty itemName;
    private final SimpleDoubleProperty currentPrice;
    private final SimpleIntegerProperty participantCount;
    private final SimpleStringProperty status;
    private final SimpleStringProperty timeRemaining;
    private final String startTimeStr;
    private final String endTimeStr;

    public AuctionRow(int stt, String auctionId, String itemName, double currentPrice,
                      int participantCount, String status, String startTimeStr, String endTimeStr, String timeRemaining) {
        this.stt              = new SimpleIntegerProperty(stt);
        this.auctionId        = new SimpleStringProperty(auctionId);
        this.itemName         = new SimpleStringProperty(itemName);
        this.currentPrice     = new SimpleDoubleProperty(currentPrice);
        this.participantCount = new SimpleIntegerProperty(participantCount);
        this.status           = new SimpleStringProperty(status);
        this.startTimeStr     = startTimeStr;
        this.endTimeStr       = endTimeStr;
        this.timeRemaining    = new SimpleStringProperty(timeRemaining);
    }

    public int    getStt()              { return stt.get(); }
    public String getAuctionId()        { return auctionId.get(); }
    public String getItemName()         { return itemName.get(); }
    public double getCurrentPrice()     { return currentPrice.get(); }
    public int    getParticipantCount() { return participantCount.get(); }
    public String getStatus()           { return status.get(); }
    public String getTimeRemaining()    { return timeRemaining.get(); }
    public String getStartTimeStr()     { return startTimeStr; }
    public String getEndTimeStr()       { return endTimeStr; }

    // -------------------------------------------------------------------------
    // Property getters (cho TableView binding)
    // -------------------------------------------------------------------------
    public SimpleIntegerProperty sttProperty()           { return stt; }
    public SimpleStringProperty  auctionIdProperty()     { return auctionId; }
    public SimpleStringProperty  itemNameProperty()      { return itemName; }
    public SimpleDoubleProperty  currentPriceProperty()  { return currentPrice; }
    public SimpleIntegerProperty participantCountProperty() { return participantCount; }
    public SimpleStringProperty  statusProperty()        { return status; }
    public SimpleStringProperty  timeRemainingProperty() { return timeRemaining; }
}
