package com.sk.smart_kitchen.services;

import org.springframework.stereotype.Service;

@Service
public class UnitConversionService {

    // Converts everything to a baseline of Grams (g) or Milliliters (ml)
    public double convertToBase(double quantity, String unit) {
        if (unit == null || unit.isBlank()) return quantity;

        switch (unit.toLowerCase().trim()) {
            // Mass
            case "kg": return quantity * 1000.0;
            case "g": return quantity;
            case "oz": return quantity * 28.35;
            
            // Volume
            case "l": return quantity * 1000.0;
            case "ml": return quantity;
            case "cup": return quantity * 240.0;
            case "tbsp": return quantity * 14.79;
            case "tsp": return quantity * 4.93;
            
            // Abstract (count, bunch, head)
            default: return quantity; 
        }
    }

    public boolean hasEnough(double recipeQty, String recipeUnit, double pantryQty, String pantryUnit) {
        double normalizedRecipe = convertToBase(recipeQty, recipeUnit);
        double normalizedPantry = convertToBase(pantryQty, pantryUnit);
        
        return normalizedPantry >= normalizedRecipe;
    }
}