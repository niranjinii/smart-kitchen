package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GapAnalysisEngine {

    public double calculateExactMatchPercentage(Recipe recipe, List<PantryItem> userPantry) {
        if (recipe.getIngredients() == null || recipe.getIngredients().isEmpty()) {
            return 0.0;
        }

        // 1. O(N) operation to build a strict HashMap of the user's pantry
        // Key: Ingredient ID, Value: Total Quantity available
        Map<Long, Double> pantryMap = userPantry.stream()
                .collect(Collectors.toMap(
                        item -> item.getIngredient().getId(),
                        PantryItem::getQuantity,
                        Double::sum 
                ));

        int totalRequired = recipe.getIngredients().size();
        int matchedRequirements = 0;

        // 2. Cross-reference the recipe requirements against the HashMap
        for (RecipeIngredient req : recipe.getIngredients()) {
            Long reqId = req.getIngredient().getId();
            Double requiredQty = req.getQuantityNeeded();

            if (pantryMap.containsKey(reqId)) {
                Double availableQty = pantryMap.get(reqId);
                // Strict 3NF check: Do they have enough of the exact ingredient?
                if (availableQty >= requiredQty) {
                    matchedRequirements++;
                }
            }
        }

        // 3. Return a clean percentage
        return ((double) matchedRequirements / totalRequired) * 100.0;
    }
}