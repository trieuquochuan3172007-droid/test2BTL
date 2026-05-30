package com.auction.common.models;

public class Electronics extends Item {
    private String brand;
    private String model;
    private String serialNumber;
    private String warrantyDate;

    public Electronics(String id, String name, String description, double initPrice, String brand, String model, String serialNumber, String warrantyDate){
        super(id, name, description, initPrice, "Electronics");
        this.brand = brand;
        this.model = model;
        this.serialNumber = serialNumber;
        this.warrantyDate = warrantyDate;
    }
    @Override
    public void showDetail(){
        System.out.println("--- Electronics Detail---");
        System.out.println("ID: " + id + " | Category: " + category);
        System.out.println("Name: " + name);
        System.out.println("Description: " + description);
        System.out.println("Initial Price: $" + initPrice);
        System.out.println("Brand: " + brand + " | Model: " + model);
        System.out.println("Serial Number: " + serialNumber + " | Warranty Date: " + warrantyDate);
    }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getWarrantyDate() { return warrantyDate; }
    public void setWarrantyDate(String warrantyDate) { this.warrantyDate = warrantyDate; }
}
