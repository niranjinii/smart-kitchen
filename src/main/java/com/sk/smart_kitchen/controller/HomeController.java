package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.SavedRecipeRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedRecipeRepository savedRecipeRepository;

   @GetMapping("/")
    public String viewFeed(Model model, java.security.Principal principal) {
        List<Recipe> allRecipes = recipeRepository.findAll();
        model.addAttribute("recipes", allRecipes);

        List<String> dynamicCategories = allRecipes.stream()
                .map(Recipe::getMealType)
                .filter(type -> type != null && !type.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());
        model.addAttribute("categories", dynamicCategories);

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

}