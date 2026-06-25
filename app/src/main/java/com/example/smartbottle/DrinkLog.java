package com.example.smartbottle;

public class DrinkLog {
    private int id;
    private int amountMl;
    private long timestamp;

    public DrinkLog(int id, int amountMl, long timestamp) {
        this.id = id;
        this.amountMl = amountMl;
        this.timestamp = timestamp;
    }

    public DrinkLog(int amountMl, long timestamp) {
        this.amountMl = amountMl;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public int getAmountMl() {
        return amountMl;
    }

    public long getTimestamp() {
        return timestamp;
    }
}