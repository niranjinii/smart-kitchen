package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    @Autowired
    private RecipeRepository recipeRepository;

    @GetMapping("/")
    public String viewFeed(Model model) {
        // 1. Fetch ALL recipes
        List<Recipe> allRecipes = recipeRepository.findAll();
        model.addAttribute("recipes", allRecipes);

        // 2. Extract unique meal types for the dynamic pills
        List<String> dynamicCategories = allRecipes.stream()
                .map(Recipe::getMealType)
                .filter(type -> type != null && !type.trim().isEmpty())
                .distinct()
                .collect(Collectors.toList());

        model.addAttribute("categories", dynamicCategories);

        return "feed";
    }

    @GetMapping("/recipes/new")
    public String showPublishPage() {
        return "publish"; // This looks for publish.html
    }

    @GetMapping("/recipes/{id}")
    public String showRecipeView(@PathVariable Long id, Model model) {

        // NOTE: Right now, this just returns a static dummy HTML.
        // Later, when your database is ready, you will do something like this:
        // Recipe recipe = recipeRepository.findById(id).orElseThrow();
        // model.addAttribute("recipe", recipe);
        return "recipe";
    }
}