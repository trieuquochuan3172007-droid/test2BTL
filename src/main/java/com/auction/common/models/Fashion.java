package com.auction.common.models;

public class Fashion extends Item{
    private String brand;
    private String size;
    private String material;

    public Fashion(String id, String name, String description, double initPrice, String brand, String size, String material) {
        super(id, name, description, initPrice, "Fashion");
        this.brand = brand;
        this.size = size;
        this.material = material;
    }

    @Override
    public void showDetail() {
        System.out.println("--- Fashion Details ---");
        System.out.println("ID: " + id + " | Category: " + category);
        System.out.println("Name: " + name);
        System.out.println("Description: " + description);
        System.out.println("Initial Price: $" + initPrice);
        System.out.println("Brand: " + brand);
        System.out.println("Size: " + size + " | Material: " + material);
    }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
}
