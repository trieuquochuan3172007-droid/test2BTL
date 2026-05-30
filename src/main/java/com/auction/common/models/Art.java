package com.auction.common.models;

public class Art extends Item{
    private String artist;
    private int yearCreated;
    private String technique;

    public Art(String id, String name, String description, double initPrice, String artist, int yearCreated, String technique){
        super(id, name, description, initPrice, "Art");
        this.artist = artist;
        this.yearCreated = yearCreated;
        this.technique = technique;
    }

    @Override
    public void showDetail() {
        System.out.println("--- Art Details ---");
        System.out.println("ID: " + id + " | Category: " + category);
        System.out.println("Name: " + name);
        System.out.println("Description: " + description);
        System.out.println("Initial Price: $" + initPrice);
        System.out.println("Artist: " + artist + " | Year Created: " + yearCreated);
        System.out.println("Technique: " + technique);
    }
    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }
    public int getYearCreated() { return yearCreated; }
    public void setYearCreated(int yearCreated) { this.yearCreated = yearCreated; }
    public String getTechnique() { return technique;}
    public void setTechnique(String technique){ this.technique=technique;}
}
