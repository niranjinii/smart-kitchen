package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GapAnalysisEngine {

    private final UnitConversionService converter;

    public GapAnalysisEngine(UnitConversionService converter) {
        this.converter = converter;
    }

    // 🌟 NEW: A smart object that holds partial credit data!
    public static class GapItem {
        public RecipeIngredient requirement;
        public double percentOwned; // e.g., 0.5 for 50%
        public double missingQty;   // The exact delta to put on the shopping list
    }

    public static class GapResult {
        public int matchPercentage;
        public List<GapItem> owned = new ArrayList<>();
        public List<GapItem> missing = new ArrayList<>(); 
    }

    public GapResult analyze(List<RecipeIngredient> recipeIngredients, List<PantryItem> userPantry) {
        GapResult result = new GapResult();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return result;

        Map<Long, PantryItem> pantryMap = userPantry.stream()
                .collect(Collectors.toMap(item -> item.getIngredient().getId(), item -> item, (e, r) -> e));

        double totalPercent = 0.0;

        for (RecipeIngredient req : recipeIngredients) {
            GapItem gapItem = new GapItem();
            gapItem.requirement = req;
            
            double reqBase = converter.convertToBase(req.getQuantityNeeded(), req.getUnit());

            if (pantryMap.containsKey(req.getIngredient().getId())) {
                PantryItem pantryItem = pantryMap.get(req.getIngredient().getId());
                double pantryBase = converter.convertToBase(pantryItem.getQuantity(), pantryItem.getUnit());
                
                if (pantryBase >= reqBase) {
                    // 100% Owned
                    gapItem.percentOwned = 1.0;
                    gapItem.missingQty = 0.0;
                    result.owned.add(gapItem);
                } else {
                    // Partial Credit!
                    gapItem.percentOwned = pantryBase / reqBase;
                    double missingBase = reqBase - pantryBase;
                    // Convert back to recipe unit and round to 1 decimal
                    double missingConverted = converter.convertFromBase(missingBase, req.getUnit());
                    gapItem.missingQty = Math.round(missingConverted * 10.0) / 10.0;
                    result.missing.add(gapItem); 
                }
            } else {
                // 0% Owned
                gapItem.percentOwned = 0.0;
                // Round to 1 decimal
                gapItem.missingQty = Math.round(req.getQuantityNeeded() * 10.0) / 10.0;
                result.missing.add(gapItem); 
            }
            totalPercent += gapItem.percentOwned;
        }

        // The final score is the average of all the partial credits!
        result.matchPercentage = (int) Math.round((totalPercent / recipeIngredients.size()) * 100.0);
        return result;
    }
}