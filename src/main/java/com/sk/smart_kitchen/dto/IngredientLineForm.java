package com.sk.smart_kitchen.dto;

public class IngredientLineForm {
    private String name;
    private String quantity; // <-- Changed to String
    private String unit;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
}