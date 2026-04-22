package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.SavedRecipeRepository;
import com.sk.smart_kitchen.repositories.TagRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedRecipeRepository savedRecipeRepository;

        @Autowired
        private TagRepository tagRepository;

   @GetMapping("/")
        public String viewFeed(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "tags", required = false) List<String> selectedTags,
        @RequestParam(value = "mealType", required = false) String mealType,
        @RequestParam(value = "minPrep", required = false) Integer minPrep,
        @RequestParam(value = "maxPrep", required = false) Integer maxPrep,
            Model model,
            java.security.Principal principal
        ) {
        List<Recipe> allRecipes = recipeRepository.findAll();

        String normalizedQuery = query != null ? query.trim() : "";
    String normalizedMealType = mealType != null ? mealType.trim() : "";
    Integer effectiveMinPrep = minPrep != null ? Math.max(0, minPrep) : 0;

    int detectedPrepMax = allRecipes.stream()
        .map(Recipe::getPrepTimeMins)
        .filter(v -> v != null && v > 0)
        .max(Integer::compareTo)
        .orElse(120);
    int prepSliderMax = Math.max(detectedPrepMax, 30);
    Integer effectiveMaxPrep = maxPrep != null ? Math.max(effectiveMinPrep, Math.min(maxPrep, prepSliderMax)) : prepSliderMax;

        Set<String> normalizedSelectedTags = selectedTags == null
            ? Collections.emptySet()
            : selectedTags.stream()
                .filter(tag -> tag != null && !tag.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());

        List<Recipe> filteredRecipes = allRecipes.stream()
            .filter(recipe -> matchesSearch(recipe, normalizedQuery))
        .filter(recipe -> matchesMealTypeFilter(recipe, normalizedMealType))
        .filter(recipe -> matchesPrepRangeFilter(recipe, effectiveMinPrep, effectiveMaxPrep))
            .filter(recipe -> matchesSecondaryTagFilter(recipe, normalizedSelectedTags))
            .collect(Collectors.toList());

        model.addAttribute("recipes", filteredRecipes);

        List<String> dynamicCategories = allRecipes.stream()
                .map(Recipe::getMealType)
                .filter(type -> type != null && !type.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        model.addAttribute("categories", dynamicCategories);

        List<String> availableTags = tagRepository.findAll().stream()
            .map(com.sk.smart_kitchen.entities.Tag::getName)
            .filter(name -> name != null && !name.trim().isEmpty())
            .map(String::trim)
            .collect(Collectors.collectingAndThen(Collectors.toCollection(TreeSet::new), ArrayList::new));

        model.addAttribute("availableTags", availableTags);
        model.addAttribute("currentSearch", normalizedQuery);
        model.addAttribute("selectedTagFilters", new ArrayList<>(normalizedSelectedTags));
        model.addAttribute("currentMealType", normalizedMealType);
        model.addAttribute("currentMinPrep", effectiveMinPrep);
        model.addAttribute("currentMaxPrep", effectiveMaxPrep);
        model.addAttribute("prepSliderMax", prepSliderMax);

        // NEW: Tell the feed which recipes the user has saved!
        java.util.List<Long> savedRecipeIds = new java.util.ArrayList<>();
        if (principal != null) {
            com.sk.smart_kitchen.entities.User user = userRepository.findByEmail(principal.getName()).get();
            savedRecipeIds = savedRecipeRepository.findByUser(user).stream()
                    .map(sr -> sr.getRecipe().getId())
                    .collect(Collectors.toList());
        }
        model.addAttribute("savedRecipeIds", savedRecipeIds);

        return "feed";
    }

    @GetMapping("/saved")
    public String showSavedRecipes(java.security.Principal principal, Model model) {
        if (principal == null) return "redirect:/login";
        
        com.sk.smart_kitchen.entities.User user = userRepository.findByEmail(principal.getName()).get();
        
        List<com.sk.smart_kitchen.entities.SavedRecipe> savedList = savedRecipeRepository.findByUser(user);
        
        List<Recipe> savedRecipes = savedList.stream()
                .map(com.sk.smart_kitchen.entities.SavedRecipe::getRecipe)
                .collect(Collectors.toList());
        model.addAttribute("recipes", savedRecipes);
        model.addAttribute("categories", List.of("My Bookmarks")); 
        model.addAttribute("availableTags", List.of());
        model.addAttribute("currentSearch", "");
        model.addAttribute("selectedTagFilters", List.of());
        model.addAttribute("currentMealType", "");
        model.addAttribute("currentMinPrep", 0);
        model.addAttribute("currentMaxPrep", 120);
        model.addAttribute("prepSliderMax", 120);

        // Tell the feed that ALL of these are saved (so they light up green)
        java.util.List<Long> savedRecipeIds = savedRecipes.stream().map(Recipe::getId).collect(Collectors.toList());
        model.addAttribute("savedRecipeIds", savedRecipeIds);

        return "feed"; 
    }

    private boolean matchesSearch(Recipe recipe, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String needle = query.toLowerCase(Locale.ROOT);

        boolean titleMatch = recipe.getTitle() != null && recipe.getTitle().toLowerCase(Locale.ROOT).contains(needle);
        boolean descMatch = recipe.getDescription() != null && recipe.getDescription().toLowerCase(Locale.ROOT).contains(needle);
        boolean mealTypeMatch = recipe.getMealType() != null && recipe.getMealType().toLowerCase(Locale.ROOT).contains(needle);
        boolean secondaryTagMatch = recipe.getTags() != null && recipe.getTags().stream()
                .map(com.sk.smart_kitchen.entities.Tag::getName)
                .anyMatch(tagName -> tagName != null && tagName.toLowerCase(Locale.ROOT).contains(needle));

        return titleMatch || descMatch || mealTypeMatch || secondaryTagMatch;
    }

    private boolean matchesSecondaryTagFilter(Recipe recipe, Set<String> selectedTags) {
        if (selectedTags == null || selectedTags.isEmpty()) {
            return true;
        }
        if (recipe.getTags() == null || recipe.getTags().isEmpty()) {
            return false;
        }

        Set<String> recipeTags = recipe.getTags().stream()
                .map(com.sk.smart_kitchen.entities.Tag::getName)
                .filter(name -> name != null && !name.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());

        return recipeTags.containsAll(selectedTags);
    }

    private boolean matchesMealTypeFilter(Recipe recipe, String mealType) {
        if (mealType == null || mealType.isBlank()) {
            return true;
        }
        return recipe.getMealType() != null && recipe.getMealType().equalsIgnoreCase(mealType);
    }

    private boolean matchesPrepRangeFilter(Recipe recipe, Integer minPrep, Integer maxPrep) {
        if (minPrep == null || maxPrep == null) {
            return true;
        }
        Integer prep = recipe.getPrepTimeMins();
        if (prep == null) {
            return false;
        }
        return prep >= minPrep && prep <= maxPrep;
    }
}