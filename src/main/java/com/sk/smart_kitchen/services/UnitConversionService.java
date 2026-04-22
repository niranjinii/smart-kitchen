package com.sk.smart_kitchen.services;

import org.springframework.stereotype.Service;

@Service
public class UnitConversionService {

    public String canonicalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) return "unit";

        switch (unit.toLowerCase().trim()) {
            case "kg":
            case "g":
            case "oz":
                return "g";
            case "l":
            case "ml":
            case "cup":
            case "tbsp":
            case "tsp":
                return "ml";
            case "count":
            case "piece":
            case "pieces":
            case "pc":
            case "pcs":
            case "bunch":
            case "head":
                return "count";
            default:
                return "unit";
        }
    }

    public double convertToBase(double quantity, String unit) {
        if (unit == null || unit.isBlank()) return quantity;
        switch (unit.toLowerCase().trim()) {
            case "kg": return quantity * 1000.0;
            case "g": return quantity;
            case "oz": return quantity * 28.35;
            case "l": return quantity * 1000.0;
            case "ml": return quantity;
            case "cup": return quantity * 240.0;
            case "tbsp": return quantity * 14.79;
            case "tsp": return quantity * 4.93;
            case "count":
            case "piece":
            case "pieces":
            case "pc":
            case "pcs":
            case "bunch":
            case "head":
            case "unit":
                return quantity;
            default: return quantity; 
        }
    }

    // 🌟 NEW: Converts the missing amount BACK into the recipe's unit for the shopping list!
    public double convertFromBase(double baseQuantity, String targetUnit) {
        if (targetUnit == null || targetUnit.isBlank()) return baseQuantity;
        switch (targetUnit.toLowerCase().trim()) {
            case "kg": return baseQuantity / 1000.0;
            case "g": return baseQuantity;
            case "oz": return baseQuantity / 28.35;
            case "l": return baseQuantity / 1000.0;
            case "ml": return baseQuantity;
            case "cup": return baseQuantity / 240.0;
            case "tbsp": return baseQuantity / 14.79;
            case "tsp": return baseQuantity / 4.93;
            case "count":
            case "piece":
            case "pieces":
            case "pc":
            case "pcs":
            case "bunch":
            case "head":
            case "unit":
                return baseQuantity;
            default: return baseQuantity;
        }
    }

    public boolean hasEnough(double recipeQty, String recipeUnit, double pantryQty, String pantryUnit) {
        return convertToBase(pantryQty, pantryUnit) >= convertToBase(recipeQty, recipeUnit);
    }
}