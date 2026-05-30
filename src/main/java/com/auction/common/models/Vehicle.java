package com.auction.common.models;

public class Vehicle extends Item {
    private String brand;
    private String model;
    private int mileage;

    public Vehicle(String id, String name, String description, double initPrice, String brand, String model, int mileage) {
        super(id, name, description, initPrice, "Vehicle");
        this.brand = brand;
        this.model = model;
        this.mileage = mileage;
    }

    @Override
    public void showDetail() {
        System.out.println("--- Vehicle Details ---");
        System.out.println("ID: " + id + " | Category: " + category);
        System.out.println("Name: " + name);
        System.out.println("Description: " + description);
        System.out.println("Initial Price: $" + initPrice);
        System.out.println("Brand: " + brand + " | Model: " + model);
        System.out.println("Mileage: " + mileage);
    }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getMileage() { return mileage; }
    public void setMileage(int mileage) { this.mileage = mileage; }
}
