package com.example.stackt;

import java.util.Date;

public class Transaction {
    private String description;
    private String category;
    private double amount;
    private Date date;

    public Transaction(String description, String category, double amount, Date date) {
        this.description = description;
        this.category = category;
        this.amount = amount;
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public double getAmount() {
        return amount;
    }

    public Date getDate() {
        return date;
    }
}
