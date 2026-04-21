package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.*;
import com.sk.smart_kitchen.repositories.SubstitutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GapAnalysisEngine {

    @Autowired
    public IngredientNormalizer normalizer;

    @Autowired
    public UnitConversionService converter;

    @Autowired
    private SubstitutionRepository substitutionRepository;

    public static class GapResult {
        public List<GapItem> owned = new ArrayList<>();
        public List<GapItem> missing = new ArrayList<>();
        public int matchPercentage = 0;
    }

    public static class GapItem {
        public RecipeIngredient requirement;
        public double percentOwned; 
        public double missingQty;   
        
        public boolean isSubstituted = false;
        public String substituteName = null;
        public double substituteQtyUsed = 0.0;
        public String substituteUnit = null;
        
        public String originalName = null; 
        public Long actualIngredientId = null; 
        
        public boolean isRecipeScopedSwap = false; 

        public List<String> possibleSubstitutes = new ArrayList<>();
    }

    public GapResult analyze(List<RecipeIngredient> recipeIngredients, List<PantryItem> userPantry, User user) {
        GapResult result = new GapResult();
        if (recipeIngredients == null || recipeIngredients.isEmpty()) return result;

        // 🌟 Filters out expired food before doing any math!
        Map<Long, PantryItem> pantryMap = userPantry.stream()
                .filter(item -> item.getExpirationDate() == null || !item.getExpirationDate().isBefore(java.time.LocalDate.now()))
                .collect(Collectors.toMap(item -> item.getIngredient().getId(), item -> item, (e, r) -> e));

        double totalPercent = 0.0;

        for (RecipeIngredient req : recipeIngredients) {
            GapItem gapItem = new GapItem();
            gapItem.requirement = req;
            double reqBase = converter.convertToBase(req.getQuantityNeeded(), req.getUnit());
            
            Ingredient targetIngredient = req.getIngredient();
            gapItem.actualIngredientId = targetIngredient.getId();

            // 1. USER PREFERENCE OVERRIDE
            List<Substitution> userPrefs = substitutionRepository.findByOriginalIngredientAndUser(targetIngredient, user);
            Substitution activePref = null;
            for (Substitution sub : userPrefs) {
                if (sub.getRecipe() != null && sub.getRecipe().getId().equals(req.getRecipe().getId())) {
                    activePref = sub; break; 
                } else if (sub.getRecipe() == null) {
                    activePref = sub; 
                }
            }

            if (activePref != null) {
                targetIngredient = activePref.getSubstituteIngredient();
                gapItem.actualIngredientId = targetIngredient.getId();
                gapItem.isSubstituted = true;
                gapItem.originalName = req.getIngredient().getName();
                gapItem.substituteName = targetIngredient.getName();
                gapItem.isRecipeScopedSwap = (activePref.getRecipe() != null);
                reqBase = reqBase * (activePref.getConversionMultiplier() != null ? activePref.getConversionMultiplier() : 1.0);
            }

            // 2. PANTRY CHECK FOR TARGET
            if (checkAndApplyPantryItem(pantryMap, targetIngredient.getId(), reqBase, gapItem, req)) {
                if (activePref != null) {
                    PantryItem pItem = pantryMap.get(targetIngredient.getId());
                    gapItem.substituteQtyUsed = Math.round(converter.convertFromBase(reqBase, pItem.getUnit()) * 10.0) / 10.0;
                    gapItem.substituteUnit = pItem.getUnit();
                }
                result.owned.add(gapItem);
            } 
            else {
                // 3. MISSING LOGIC
                if (activePref != null) {
                    gapItem.missingQty = Math.round(converter.convertFromBase(reqBase, req.getUnit()) * 10.0) / 10.0; 
                } else {
                    // 🌟 FIXED: No more aggressive auto-swapping! Just gather the lightbulbs.
                    List<Substitution> authorSubs = substitutionRepository.findByOriginalIngredientAndRecipe(req.getIngredient(), req.getRecipe());
                    for (Substitution sub : authorSubs) {
                        gapItem.possibleSubstitutes.add(sub.getSubstituteIngredient().getName()); 
                    }
                    gapItem.missingQty = Math.round(converter.convertFromBase(reqBase, req.getUnit()) * 10.0) / 10.0;
                }
                result.missing.add(gapItem);
            }
            totalPercent += gapItem.percentOwned;
        }

        result.matchPercentage = (int) Math.round((totalPercent / recipeIngredients.size()) * 100.0);
        return result;
    }

    private boolean checkAndApplyPantryItem(Map<Long, PantryItem> pantryMap, Long ingredientId, double reqBase, GapItem gapItem, RecipeIngredient req) {
        if (pantryMap.containsKey(ingredientId)) {
            PantryItem pItem = pantryMap.get(ingredientId);
            double pantryBase = converter.convertToBase(pItem.getQuantity(), pItem.getUnit());

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
        } else {
            gapItem.percentOwned = 0.0;
            gapItem.missingQty = Math.round(converter.convertFromBase(reqBase, req.getUnit()) * 10.0) / 10.0;
            return false;
        }
    }
}