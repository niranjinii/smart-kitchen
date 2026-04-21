package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import com.sk.smart_kitchen.entities.Substitution;
import com.sk.smart_kitchen.repositories.SubstitutionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class GapAnalysisEngine {

    public final UnitConversionService converter;
    public final IngredientNormalizer normalizer; // 🌟 1. Add it here as a public field!
    private final SubstitutionRepository substitutionRepository;

    // 🌟 2. Inject it into the constructor!
    public GapAnalysisEngine(UnitConversionService converter, 
                             IngredientNormalizer normalizer, 
                             SubstitutionRepository substitutionRepository) {
        this.converter = converter;
        this.normalizer = normalizer; // 🌟 3. Assign it!
        this.substitutionRepository = substitutionRepository;
    }

    public static class GapItem {
        public RecipeIngredient requirement;
        public double percentOwned; 
        public double missingQty;   
        
        public boolean isSubstituted = false;
        public String substituteName = null;
        public double substituteQtyUsed = 0.0;
        public String substituteUnit = null;

        // Holds author suggestions to show in the UI
        public List<String> possibleSubstitutes = new ArrayList<>();
    }
    public static class GapResult {
        public int matchPercentage;
        public List<GapItem> owned = new ArrayList<>();
        public List<GapItem> missing = new ArrayList<>(); 
    }

    public GapResult analyze(List<RecipeIngredient> recipeIngredients, List<PantryItem> userPantry, com.sk.smart_kitchen.entities.User user) {
        GapResult result = new GapResult();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return result;

        Map<Long, PantryItem> pantryMap = userPantry.stream()
                .collect(Collectors.toMap(item -> item.getIngredient().getId(), item -> item, (e, r) -> e));

        double totalPercent = 0.0;

        for (RecipeIngredient req : recipeIngredients) {
            GapItem gapItem = new GapItem();
            gapItem.requirement = req;
            double reqBase = converter.convertToBase(req.getQuantityNeeded(), req.getUnit());

            // 1. USER PREFERENCE OVERRIDE
            Ingredient targetIngredient = req.getIngredient();
            var userPref = substitutionRepository.findByOriginalIngredientAndUser(targetIngredient, user);
            
            if (userPref.isPresent()) {
                targetIngredient = userPref.get().getSubstituteIngredient();
                gapItem.isSubstituted = true;
                gapItem.substituteName = targetIngredient.getName();
                gapItem.substituteQtyUsed = req.getQuantityNeeded(); // Assuming 1:1 for UI simplicity
                gapItem.substituteUnit = req.getUnit();
            }

            // 2. CHECK PANTRY FOR THE TARGET
            if (checkAndApplyPantryItem(pantryMap, targetIngredient.getId(), reqBase, gapItem, req)) {
                result.owned.add(gapItem);
            } 
            else {
                // 3. IF MISSING AND NO PERSONAL OVERRIDE, CHECK AUTHOR SUGGESTIONS
                boolean foundAuthorSub = false;
                if (userPref.isEmpty()) {
                    List<Substitution> authorSubs = substitutionRepository.findByOriginalIngredientAndRecipe(req.getIngredient(), req.getRecipe());
                    for (Substitution sub : authorSubs) {
                        gapItem.possibleSubstitutes.add(sub.getSubstituteIngredient().getName()); // Save lightbulb
                        
                        if (pantryMap.containsKey(sub.getSubstituteIngredient().getId())) {
                            PantryItem pItem = pantryMap.get(sub.getSubstituteIngredient().getId());
                            double pantryBase = converter.convertToBase(pItem.getQuantity(), pItem.getUnit());
                            double requiredSubBase = reqBase * (sub.getConversionMultiplier() != null ? sub.getConversionMultiplier() : 1.0);

                            if (pantryBase >= requiredSubBase) {
                                gapItem.percentOwned = 1.0;
                                gapItem.missingQty = 0.0;
                                gapItem.isSubstituted = true;
                                gapItem.substituteName = sub.getSubstituteIngredient().getName();
                                gapItem.substituteQtyUsed = Math.round(converter.convertFromBase(requiredSubBase, pItem.getUnit()) * 10.0) / 10.0;
                                gapItem.substituteUnit = pItem.getUnit();
                                result.owned.add(gapItem);
                                foundAuthorSub = true;
                                break; 
                            }
                        }
                    }
                }
                if (!foundAuthorSub) result.missing.add(gapItem);
            }
            totalPercent += gapItem.percentOwned;
        }

        result.matchPercentage = (int) Math.round((totalPercent / recipeIngredients.size()) * 100.0);
        return result;
    }

    private boolean checkAndApplyPantryItem(Map<Long, PantryItem> pantryMap, Long ingId, double reqBase, GapItem gapItem, RecipeIngredient req) {
        if (pantryMap.containsKey(ingId)) {
            PantryItem pantryItem = pantryMap.get(ingId);
            double pantryBase = converter.convertToBase(pantryItem.getQuantity(), pantryItem.getUnit());
            
            if (pantryBase >= reqBase) {
                gapItem.percentOwned = 1.0;
                gapItem.missingQty = 0.0;
                return true;
            } else {
                gapItem.percentOwned = pantryBase / reqBase;
                double missingBase = reqBase - pantryBase;
                gapItem.missingQty = Math.round(converter.convertFromBase(missingBase, req.getUnit()) * 10.0) / 10.0;
                return false;
            }
        }
        gapItem.percentOwned = 0.0;
        gapItem.missingQty = Math.round(req.getQuantityNeeded() * 10.0) / 10.0;
        return false;
    }

    private boolean isBadContextSwap(RecipeIngredient req, Substitution sub) {
        String recipeMealType = req.getRecipe().getMealType();
        String original = sub.getOriginalIngredient().getName().toLowerCase();
        String substitute = sub.getSubstituteIngredient().getName().toLowerCase();

        // Prevent swapping Eggs for Applesauce UNLESS it's Breakfast or Snack (Baking proxy)
        if (original.contains("egg") && substitute.contains("applesauce")) {
            return recipeMealType != null && (recipeMealType.equalsIgnoreCase("Lunch") || recipeMealType.equalsIgnoreCase("Dinner"));
        }
        return false;
    }
}