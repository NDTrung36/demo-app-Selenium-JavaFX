package com.trung;

public class Product {
    private String asin;
    private String title;
    private String price;
    private String imageUrl;
    private String stock;

    public Product(String asin, String title, String price, String imageUrl, String stock) {
        this.asin = asin;
        this.title = title;
        this.price = price;
        this.imageUrl = imageUrl;
        this.stock = stock;
    }

    public String getAsin() {
        return asin;
    }

    public String getTitle() {
        return title;
    }

    public String getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }
    public String getStock() {
        return stock;
    }
}