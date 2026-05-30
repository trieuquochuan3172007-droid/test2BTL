package com.auction.common.models;

public class Furniture extends Item{
    private String material;
    private String dimensions;

    public Furniture(String id, String name, String description, double initPrice, String material, String dimensions) {
        super(id, name, description, initPrice, "Furniture");
        this.material = material;
        this.dimensions = dimensions;
    }

    @Override
    public void showDetail() {
        System.out.println("--- Furniture Details ---");
        System.out.println("ID: " + id + " | Category: " + category);
        System.out.println("Name: " + name);
        System.out.println("Description: " + description);
        System.out.println("Initial Price: $" + initPrice);
        System.out.println("Material: " + material + " | Dimensions: " + dimensions);
    }
    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public String getDimensions() { return dimensions; }
    public void setDimensions(String dimensions) { this.dimensions = dimensions; }
}
