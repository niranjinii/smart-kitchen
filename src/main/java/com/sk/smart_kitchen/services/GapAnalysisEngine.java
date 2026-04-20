package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.Recipe;
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

    // A simple DTO to package the results for the HTML
    public static class GapResult {
        public int matchPercentage;
        public List<RecipeIngredient> owned = new ArrayList<>();
        public List<RecipeIngredient> missing = new ArrayList<>();
    }

    // 🌟 CHANGE 1: Accept List<RecipeIngredient> instead of the Recipe object
    public GapResult analyze(List<RecipeIngredient> recipeIngredients, List<PantryItem> userPantry) {
        GapResult result = new GapResult();
        
        if (recipeIngredients == null || recipeIngredients.isEmpty()) {
            result.matchPercentage = 0;
            return result;
        }

        Map<Long, PantryItem> pantryMap = userPantry.stream()
                .collect(Collectors.toMap(
                        item -> item.getIngredient().getId(),
                        item -> item,
                        (existing, replacement) -> existing
                ));

        // 🌟 CHANGE 2: Loop through the list we passed in
        for (RecipeIngredient req : recipeIngredients) {
            Long reqId = req.getIngredient().getId();

            if (pantryMap.containsKey(reqId)) {
                PantryItem pantryItem = pantryMap.get(reqId);
                
                boolean enough = converter.hasEnough(
                        req.getQuantityNeeded(), req.getUnit(), 
                        pantryItem.getQuantity(), pantryItem.getUnit()
                );

                if (enough) {
                    result.owned.add(req);
                } else {
                    result.missing.add(req); 
                }
            } else {
                result.missing.add(req); 
            }
        }

        double total = recipeIngredients.size();
        result.matchPercentage = (int) Math.round((result.owned.size() / total) * 100.0);
        
        return result;
    }
}