package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.dto.RecipeForm;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.SavedRecipe;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.services.RecipeService;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;

    @Autowired
    private com.sk.smart_kitchen.repositories.ReviewRepository reviewRepository;
    
    @Autowired
    private com.sk.smart_kitchen.repositories.SavedRecipeRepository savedRecipeRepository;
    
    @Autowired
    private com.sk.smart_kitchen.repositories.UserRepository userRepository;

    @Autowired
    private com.sk.smart_kitchen.repositories.ChefsNoteRepository chefsNoteRepository;

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
        try {
            Recipe created = recipeService.createRecipe(recipeForm);
            return "redirect:/recipes/" + created.getId();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    @GetMapping("/{id}")
    public String showRecipeView(@PathVariable Long id, java.security.Principal principal, Model model) {
        Recipe recipe = getRecipeOr404(id);
        model.addAttribute("recipe", recipe);
        model.addAttribute("recipeId", id);
        
        // Load Ingredients & Instructions
        model.addAttribute("ingredients", recipeService.findRecipeIngredients(id));
        model.addAttribute("instructionSteps", recipeService.toInstructionSteps(recipe));

        // Load Reviews
        java.util.List<com.sk.smart_kitchen.entities.Review> reviews = reviewRepository.findAll().stream()
                .filter(r -> r.getRecipe().getId().equals(id))
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("reviews", reviews);

        // Check if Bookmarked
        boolean isSaved = false;
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if(user != null) {
                isSaved = savedRecipeRepository.findByUserAndRecipe(user, recipe).isPresent();
            }
        }
        model.addAttribute("isSaved", isSaved);
        
        // Load the user's Chef Note
        com.sk.smart_kitchen.entities.ChefsNote myNote = null;
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if(user != null) {
                myNote = chefsNoteRepository.findFirstByUserAndRecipe(user, recipe).orElse(null);
            }
        }
        model.addAttribute("myNote", myNote);

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
        try {
            Recipe updated = recipeService.updateRecipe(id, recipeForm);
            return "redirect:/recipes/" + updated.getId();
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
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
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
        return "redirect:/";
    }

    private Recipe getRecipeOr404(Long id) {
        try {
            return recipeService.findRecipeOrThrow(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        }
    }

    @PostMapping("/{id}/save")
    @ResponseBody
    public ResponseEntity<?> toggleBookmark(@PathVariable Long id, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        // FIXED: Replaced recipeRepository with your existing getRecipeOr404 method
        Recipe recipe = getRecipeOr404(id);

        Optional<SavedRecipe> existing = savedRecipeRepository.findByUserAndRecipe(user, recipe);
        
        // TOGGLE LOGIC: If it exists, delete it. If not, save it.
        if (existing.isPresent()) {
            savedRecipeRepository.delete(existing.get());
            return ResponseEntity.ok(Map.of("status", "removed"));
        } else {
            SavedRecipe savedRecipe = new SavedRecipe();
            savedRecipe.setUser(user);
            savedRecipe.setRecipe(recipe);
            savedRecipeRepository.save(savedRecipe);
            return ResponseEntity.ok(Map.of("status", "added"));
        }
    }

    @ModelAttribute("mealTypeOptions")
    public List<String> mealTypeOptions() {
        return List.of("Breakfast", "Lunch", "Dinner", "Snack", "Beverage");
    }
}