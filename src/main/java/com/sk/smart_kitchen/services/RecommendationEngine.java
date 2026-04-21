package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import com.sk.smart_kitchen.entities.Substitution;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.PantryItemRepository;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.SubstitutionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RecommendationEngine {

    private static final long CACHE_TTL_MILLIS = 45_000;

    private static final class CacheEntry {
        private final String pantrySignature;
        private final long createdAtMillis;
        private final List<RecipeMatchDTO> results;

        private CacheEntry(String pantrySignature, long createdAtMillis, List<RecipeMatchDTO> results) {
            this.pantrySignature = pantrySignature;
            this.createdAtMillis = createdAtMillis;
            this.results = results;
        }
    }

    private final ConcurrentMap<Long, CacheEntry> recommendationCache = new ConcurrentHashMap<>();

    @Autowired
    private RecipeRepository recipeRepository;
    
    @Autowired
    private PantryItemRepository pantryItemRepository;
    
    @Autowired
    private SubstitutionRepository substitutionRepository;

    // 🌟 TRUE OCP: Spring automatically injects any class implementing RecommendationStep!
    @Autowired
    private List<RecommendationStep> pipeline;

    // --- 1. THE DTO ---
    public static class RecipeMatchDTO {
        public Long recipeId;
        public String title;
        public String imageUrl;
        public Integer prepTime;
        public int matchPercentage;
        public int missingCount;
        public String mealType;
    }

    // --- 2. CHAIN OF RESPONSIBILITY CONTEXT ---
    public static class RecommendationContext {
        public User user;
        public List<PantryItem> pantry;
        public List<Recipe> allRecipes;
        public Map<Long, PantryItem> pantryByIngredientId = new HashMap<>();
        public Map<Long, List<Substitution>> userSubsByOriginalId = new HashMap<>();
        public List<RecipeMatchDTO> results = new ArrayList<>();
    }

    // --- 3. COR INTERFACE ---
    public interface RecommendationStep {
        void process(RecommendationContext context);
    }

    // --- 4. CONCRETE COR STEPS (Now true Spring Components!) ---

    @Component
    @Order(1) // Runs First
    public static class CalculateMatchStep implements RecommendationStep {
        @Autowired
        private UnitConversionService converter;

        @Override
        public void process(RecommendationContext context) {
            List<RecipeMatchDTO> matches = context.allRecipes.parallelStream().map(recipe -> {
                RecipeMatchDTO dto = new RecipeMatchDTO();
                dto.recipeId = recipe.getId();
                dto.title = recipe.getTitle();
                dto.imageUrl = recipe.getImageUrl();
                dto.prepTime = recipe.getPrepTimeMins();
                dto.mealType = recipe.getMealType();

                int consideredIngredients = 0;
                int missingCount = 0;
                double totalPercent = 0.0;

                for (RecipeIngredient req : recipe.getRecipeIngredients()) {
                    if (req == null || req.getIngredient() == null) continue;

                    Long originalId = req.getIngredient().getId();
                    if (originalId == null) continue;

                    double quantityNeeded = req.getQuantityNeeded() != null ? req.getQuantityNeeded() : 0.0;
                    double reqBase = converter.convertToBase(quantityNeeded, req.getUnit());
                    if (reqBase <= 0.0) continue;

                    Long targetIngredientId = originalId;
                    Substitution activePref = resolveActivePreference(
                            context.userSubsByOriginalId.getOrDefault(originalId, Collections.emptyList()),
                            recipe.getId()
                    );
                    if (activePref != null && activePref.getSubstituteIngredient() != null
                            && activePref.getSubstituteIngredient().getId() != null) {
                        targetIngredientId = activePref.getSubstituteIngredient().getId();
                        double multiplier = activePref.getConversionMultiplier() != null ? activePref.getConversionMultiplier() : 1.0;
                        reqBase = reqBase * multiplier;
                    }

                    consideredIngredients++;
                    PantryItem pantryItem = context.pantryByIngredientId.get(targetIngredientId);
                    double percentOwned = 0.0;

                    if (pantryItem != null && pantryItem.getQuantity() != null && pantryItem.getQuantity() > 0) {
                        double pantryBase = converter.convertToBase(pantryItem.getQuantity(), pantryItem.getUnit());
                        if (pantryBase > 0) {
                            percentOwned = Math.min(1.0, pantryBase / reqBase);
                        }
                    }

                    totalPercent += percentOwned;
                    if (percentOwned < 1.0) missingCount++;
                }

                dto.matchPercentage = consideredIngredients == 0
                        ? 0
                        : (int) Math.round((totalPercent / consideredIngredients) * 100.0);
                dto.missingCount = missingCount;
                return dto;
            }).collect(Collectors.toList());
            
            context.results.addAll(matches);
        }

        private Substitution resolveActivePreference(List<Substitution> subs, Long recipeId) {
            if (subs == null || subs.isEmpty()) return null;
            Substitution global = null;
            for (Substitution sub : subs) {
                if (sub == null) continue;
                if (sub.getRecipe() != null && sub.getRecipe().getId() != null && sub.getRecipe().getId().equals(recipeId)) {
                    return sub;
                }
                if (sub.getRecipe() == null && global == null) {
                    global = sub;
                }
            }
            return global;
        }
    }

    @Component
    @Order(2) // Runs Second
    public static class FilterAndSortStep implements RecommendationStep {
        @Override
        public void process(RecommendationContext context) {
            context.results = context.results.stream()
                    .sorted(Comparator.comparingInt((RecipeMatchDTO m) -> m.matchPercentage).reversed()
                    .thenComparingInt(m -> m.missingCount)
                    .thenComparingInt(m -> m.prepTime != null ? m.prepTime : Integer.MAX_VALUE)
                    .thenComparingLong(m -> m.recipeId != null ? m.recipeId : Long.MAX_VALUE))
                .limit(5)
                    .collect(Collectors.toList());
        }
    }

    // --- 5. THE ENGINE EXECUTION ---
    public List<RecipeMatchDTO> getTopRecommendations(User user) {
        RecommendationContext context = new RecommendationContext();
        context.user = user;
        context.pantry = pantryItemRepository.findByUserOrderByIdDesc(user);

        for (PantryItem item : context.pantry) {
            if (item == null || item.getIngredient() == null || item.getIngredient().getId() == null) continue;
            if (item.getExpirationDate() != null && item.getExpirationDate().isBefore(LocalDate.now())) continue;
            context.pantryByIngredientId.putIfAbsent(item.getIngredient().getId(), item);
        }

        String pantrySignature = buildPantrySignature(context.pantryByIngredientId);
        CacheEntry existing = recommendationCache.get(user.getId());
        long now = System.currentTimeMillis();
        if (existing != null
                && existing.pantrySignature.equals(pantrySignature)
                && (now - existing.createdAtMillis) <= CACHE_TTL_MILLIS) {
            return cloneResults(existing.results);
        }

        context.allRecipes = recipeRepository.findAllWithIngredients();
        if (context.allRecipes.isEmpty()) return new ArrayList<>();

        Set<com.sk.smart_kitchen.entities.Ingredient> originals = new LinkedHashSet<>();
        for (Recipe recipe : context.allRecipes) {
            for (RecipeIngredient req : recipe.getRecipeIngredients()) {
                if (req != null && req.getIngredient() != null) {
                    originals.add(req.getIngredient());
                }
            }
        }

        if (!originals.isEmpty()) {
            List<Substitution> userSubs = substitutionRepository.findByUserAndOriginalIngredientIn(user, originals);
            context.userSubsByOriginalId = userSubs.stream()
                    .filter(sub -> sub.getOriginalIngredient() != null && sub.getOriginalIngredient().getId() != null)
                    .collect(Collectors.groupingBy(sub -> sub.getOriginalIngredient().getId()));
        }

        // 🌟 Execute the dynamically injected Chain!
        for (RecommendationStep step : pipeline) {
            step.process(context);
        }

        recommendationCache.put(user.getId(), new CacheEntry(pantrySignature, now, cloneResults(context.results)));

        return context.results;
    }

    private String buildPantrySignature(Map<Long, PantryItem> pantryByIngredientId) {
        if (pantryByIngredientId == null || pantryByIngredientId.isEmpty()) return "EMPTY";

        return pantryByIngredientId.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    PantryItem item = entry.getValue();
                    String qty = item.getQuantity() == null ? "" : String.valueOf(item.getQuantity());
                    String unit = item.getUnit() == null ? "" : item.getUnit().trim().toLowerCase();
                    String expiry = item.getExpirationDate() == null ? "" : item.getExpirationDate().toString();
                    return entry.getKey() + ":" + qty + ":" + unit + ":" + expiry;
                })
                .collect(Collectors.joining("|"));
    }

    private List<RecipeMatchDTO> cloneResults(List<RecipeMatchDTO> source) {
        if (source == null || source.isEmpty()) return new ArrayList<>();

        List<RecipeMatchDTO> copy = new ArrayList<>(source.size());
        for (RecipeMatchDTO item : source) {
            RecipeMatchDTO dto = new RecipeMatchDTO();
            dto.recipeId = item.recipeId;
            dto.title = item.title;
            dto.imageUrl = item.imageUrl;
            dto.prepTime = item.prepTime;
            dto.matchPercentage = item.matchPercentage;
            dto.missingCount = item.missingCount;
            dto.mealType = item.mealType;
            copy.add(dto);
        }
        return copy;
    }
}