package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.dto.RecipeForm;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.services.RecipeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/new")
    public String showPublishPage(Model model) {
        model.addAttribute("recipeForm", recipeService.emptyForm());
        model.addAttribute("isEdit", false);
        return "publish";
    }

    @PostMapping
    public String createRecipe(@ModelAttribute("recipeForm") RecipeForm recipeForm) {
        Recipe created = recipeService.createRecipe(recipeForm);
        return "redirect:/recipes/" + created.getId();
    }

    @GetMapping("/{id}")
    public String showRecipeView(@PathVariable Long id, Model model) {
        Recipe recipe = getRecipeOr404(id);
        model.addAttribute("recipe", recipe);
        model.addAttribute("ingredients", recipeService.findRecipeIngredients(id));
        model.addAttribute("instructionSteps", recipeService.toInstructionSteps(recipe));
        return "recipe";
    }

    @GetMapping("/{id}/edit")
    public String showEditPage(@PathVariable Long id, Model model) {
        Recipe recipe = getRecipeOr404(id);
        model.addAttribute("recipeForm", recipeService.toForm(recipe));
        model.addAttribute("isEdit", true);
        model.addAttribute("recipeId", id);
        return "publish";
    }

    @PostMapping("/{id}")
    public String updateRecipe(@PathVariable Long id, @ModelAttribute("recipeForm") RecipeForm recipeForm) {
        Recipe updated;
        try {
            updated = recipeService.updateRecipe(id, recipeForm);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        }
        return "redirect:/recipes/" + updated.getId();
    }

    @PostMapping("/upload-image")
    @ResponseBody
    public Map<String, String> uploadRecipeImage(
            @RequestParam("imageFile") MultipartFile imageFile
    ) {
        try {
            String imageUrl = recipeService.storeRecipeImage(imageFile);
            return Map.of("imageUrl", imageUrl);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{id}/delete")
    public String deleteRecipe(@PathVariable Long id) {
        try {
            recipeService.deleteRecipe(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        }
        return "redirect:/";
    }

    private Recipe getRecipeOr404(Long id) {
        try {
            return recipeService.findRecipeOrThrow(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        }
    }

    @ModelAttribute("mealTypeOptions")
    public List<String> mealTypeOptions() {
        return List.of("Breakfast", "Lunch", "Dinner", "Snack", "Beverage");
    }
}
