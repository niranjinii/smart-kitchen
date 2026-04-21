package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.dto.RecipeForm;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import com.sk.smart_kitchen.entities.SavedRecipe;
import com.sk.smart_kitchen.entities.Substitution;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.services.GapAnalysisEngine;
import com.sk.smart_kitchen.services.RecipeService;
import com.sk.smart_kitchen.services.ScraperService;
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

import java.security.Principal; 
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;



@Controller
@RequestMapping("/recipes")
public class RecipeController {

    private final RecipeService recipeService;
    private final ScraperService scraperService;

    @Autowired
    private com.sk.smart_kitchen.repositories.ReviewRepository reviewRepository;
    
    @Autowired
    private com.sk.smart_kitchen.repositories.SavedRecipeRepository savedRecipeRepository;
    
    @Autowired
    private com.sk.smart_kitchen.repositories.UserRepository userRepository;

    @Autowired
    private com.sk.smart_kitchen.repositories.ChefsNoteRepository chefsNoteRepository;

    @Autowired
    private com.sk.smart_kitchen.repositories.RecipeRepository recipeRepository;

    @Autowired
    private com.sk.smart_kitchen.repositories.IngredientRepository ingredientRepository;

    // ADDED: Needed to handle editing and replacing old ingredients
    @Autowired
    private com.sk.smart_kitchen.repositories.RecipeIngredientRepository recipeIngredientRepository;

    @Autowired
    private GapAnalysisEngine gapEngine;

    @Autowired
    private com.sk.smart_kitchen.services.UnitConversionService unitConverter;

    @Autowired
    private com.sk.smart_kitchen.repositories.PantryItemRepository pantryRepository;

    @Autowired
    private com.sk.smart_kitchen.repositories.SubstitutionRepository subRepository;



    public RecipeController(RecipeService recipeService, ScraperService scraperService) {
        this.recipeService = recipeService;
        this.scraperService = scraperService;
    }

    // 1. Handles http://localhost:8080/recipes/new
    @GetMapping("/new")
    public String showPublishPage(Model model) {
        model.addAttribute("recipeForm", recipeService.emptyForm());
        model.addAttribute("isEdit", false);
        model.addAttribute("recipeId", 0L); // Safe default for new recipes
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
    public String showRecipeView(@PathVariable Long id, Principal principal, Model model, 
                                 @RequestParam(value = "servings", required = false) Integer customServings) {

        Recipe recipe = getRecipeOr404(id);
        model.addAttribute("recipe", recipe);
        model.addAttribute("recipeId", id);

        // 2. Calculate the scaling ratio
        int baseServings = recipe.getDefaultServings() != null ? recipe.getDefaultServings() : 2;
        int targetServings = (customServings != null && customServings > 0) ? customServings : baseServings;
        double ratio = (double) targetServings / baseServings;
        model.addAttribute("targetServings", targetServings); // Pass to HTML

        // 3. Create a temporary list of scaled ingredients for the math
        List<RecipeIngredient> scaledIngredients = new ArrayList<>();
        for (RecipeIngredient ri : recipeService.findRecipeIngredients(id)) {
            RecipeIngredient temp = new RecipeIngredient();
            temp.setIngredient(ri.getIngredient()); // ID is preserved!
            temp.setUnit(ri.getUnit());
            temp.setPreparationState(ri.getPreparationState());
            temp.setQuantityNeeded(ri.getQuantityNeeded() * ratio);
            scaledIngredients.add(temp);
        }
        
        // Pass the scaled ingredients to the UI instead of the base ones
        model.addAttribute("ingredients", scaledIngredients);
        model.addAttribute("instructionSteps", recipeService.toInstructionSteps(recipe));

        // --- NEW REVIEW MATH LOGIC ---
        List<com.sk.smart_kitchen.entities.Review> allReviews = reviewRepository.findByRecipeIdOrderByIdDesc(id);
        
        double averageRating = allReviews.isEmpty() ? 0.0 : allReviews.stream().mapToInt(com.sk.smart_kitchen.entities.Review::getRating).average().orElse(0.0);

        com.sk.smart_kitchen.entities.Review currentUserReview = null;
        if (principal != null) {
            com.sk.smart_kitchen.entities.User currentUser = userRepository.findByEmail(principal.getName()).orElse(null);
            if (currentUser != null) {
                currentUserReview = allReviews.stream()
                        .filter(r -> r.getUser().getId().equals(currentUser.getId()))
                        .findFirst().orElse(null);

                // Remove it from the main list so we don't display it twice
                if (currentUserReview != null) {
                    allReviews.remove(currentUserReview);
                }
            }
        }

        model.addAttribute("allReviews", allReviews);
        model.addAttribute("currentUserReview", currentUserReview);
        model.addAttribute("averageRating", Math.round(averageRating * 10) / 10.0); 
        model.addAttribute("totalReviews", allReviews.size() + (currentUserReview != null ? 1 : 0));

        // --- TEAMMATE'S LOGIC (PRESERVED) ---
        // Check if Bookmarked
        boolean isSaved = false;
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if(user != null) {
                isSaved = savedRecipeRepository.findByUserAndRecipe(user, recipe).isPresent();
            }
        }
        model.addAttribute("isSaved", isSaved);
        
        com.sk.smart_kitchen.entities.ChefsNote myNote = null;
        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElse(null);
            if(user != null) {
                myNote = chefsNoteRepository.findFirstByUserAndRecipe(user, recipe).orElse(null);
            }
        }
        model.addAttribute("myNote", myNote);

        if (principal != null) {
            User user = userRepository.findByEmail(principal.getName()).orElseThrow();
            List<com.sk.smart_kitchen.entities.PantryItem> pantry = pantryRepository.findByUserOrderByIdDesc(user);
            
            model.addAttribute("userPantryItems", pantry); // 🌟 NEW: Pass pantry for the Swap Modal!
            
           GapAnalysisEngine.GapResult gapResult = gapEngine.analyze(scaledIngredients, pantry, user);
            model.addAttribute("gapResult", gapResult);
        }
        return "recipe";
    }

    @PostMapping("/{id}/swap")
    public String manualSubstitute(@PathVariable Long id, Principal principal, 
                                   @RequestParam("originalId") Long originalId, 
                                   @RequestParam(value = "pantryItemId", required = false) Long pantryItemId,
                                   @RequestParam(value = "customIngredientName", required = false) String customName,
                                   @RequestParam(value = "recipeScope", defaultValue = "false") boolean recipeScope) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Ingredient original = ingredientRepository.findById(originalId).orElseThrow();
        Ingredient substitute;

        if (pantryItemId != null) substitute = pantryRepository.findById(pantryItemId).orElseThrow().getIngredient();
        else if (customName != null && !customName.trim().isEmpty()) {
            String cleanName = gapEngine.normalizer.normalize(customName);
            substitute = ingredientRepository.findByNameIgnoreCase(cleanName)
                    .orElseGet(() -> {
                        Ingredient newIng = new Ingredient();
                        newIng.setName(cleanName);
                        return ingredientRepository.save(newIng);
                    });
        } else return "redirect:/recipes/" + id;

        // Find existing preference matching the scope
        Substitution newSub = subRepository.findByOriginalIngredientAndUser(original, user)
                .stream().filter(s -> recipeScope ? (s.getRecipe() != null && s.getRecipe().getId().equals(id)) : s.getRecipe() == null)
                .findFirst().orElse(new Substitution());

        newSub.setOriginalIngredient(original);
        newSub.setSubstituteIngredient(substitute);
        newSub.setUser(user);
        
        if (recipeScope) {
            newSub.setRecipe(recipeService.findRecipeOrThrow(id));
            newSub.setNotes("Author suggestion explicitly accepted for this recipe");
        } else {
            newSub.setNotes("Manual permanent user preference");
        }
        
        newSub.setConversionMultiplier(1.0);
        subRepository.save(newSub);
        return "redirect:/recipes/" + id; 
    }

    @GetMapping("/{id}/cook")
    public String showCookMode(@PathVariable Long id, Model model, java.security.Principal principal,
                               @RequestParam(value = "servings", required = false) Integer customServings) {
        if (principal == null) return "redirect:/login?continue=/recipes/" + id + "/cook";
        Recipe recipe = getRecipeOr404(id);
        model.addAttribute("recipe", recipe);
        model.addAttribute("recipeId", id);

        int baseServings = recipe.getDefaultServings() != null ? recipe.getDefaultServings() : 2;
        int targetServings = (customServings != null && customServings > 0) ? customServings : baseServings;
        double ratio = (double) targetServings / baseServings;
        model.addAttribute("targetServings", targetServings); 

        List<RecipeIngredient> scaledIngredients = new ArrayList<>();
        for (RecipeIngredient ri : recipeService.findRecipeIngredients(id)) {
            RecipeIngredient temp = new RecipeIngredient();
            temp.setIngredient(ri.getIngredient()); 
            temp.setUnit(ri.getUnit());
            temp.setPreparationState(ri.getPreparationState());
            temp.setQuantityNeeded(ri.getQuantityNeeded() * ratio);
            scaledIngredients.add(temp);
        }
        
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<com.sk.smart_kitchen.entities.PantryItem> pantry = pantryRepository.findByUserOrderByIdDesc(user);
        
        // Pass the ENGINE result to Cook Mode so it knows what was swapped!
        GapAnalysisEngine.GapResult gapResult = gapEngine.analyze(scaledIngredients, pantry, user);
        model.addAttribute("gapResult", gapResult);
        
        model.addAttribute("instructionSteps", recipeService.toInstructionSteps(recipe));
        return "cook-mode"; 
    }

    @PostMapping("/{id}/cook/finish")
    public String finishCooking(@PathVariable Long id, Principal principal, @RequestParam("servings") Integer servings) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Recipe recipe = getRecipeOr404(id);

        int baseServings = recipe.getDefaultServings() != null ? recipe.getDefaultServings() : 2;
        double ratio = (double) servings / baseServings;

        List<RecipeIngredient> scaledIngredients = new ArrayList<>();
        for (RecipeIngredient ri : recipeService.findRecipeIngredients(id)) {
            RecipeIngredient temp = new RecipeIngredient();
            temp.setIngredient(ri.getIngredient()); 
            temp.setUnit(ri.getUnit());
            temp.setQuantityNeeded(ri.getQuantityNeeded() * ratio);
            scaledIngredients.add(temp);
        }

        List<com.sk.smart_kitchen.entities.PantryItem> pantry = pantryRepository.findByUserOrderByIdDesc(user);
        java.util.Map<Long, com.sk.smart_kitchen.entities.PantryItem> pantryMap = pantry.stream()
                .collect(java.util.stream.Collectors.toMap(item -> item.getIngredient().getId(), item -> item, (e, r) -> e));

        // Use the engine so we know exactly what to deduct!
        GapAnalysisEngine.GapResult gapResult = gapEngine.analyze(scaledIngredients, pantry, user);

        for (GapAnalysisEngine.GapItem item : gapResult.owned) {
            if (pantryMap.containsKey(item.actualIngredientId)) {
                com.sk.smart_kitchen.entities.PantryItem pItem = pantryMap.get(item.actualIngredientId);
                
                double deductionBase = 0;
                if (item.isSubstituted) {
                    deductionBase = gapEngine.converter.convertToBase(item.substituteQtyUsed, item.substituteUnit);
                } else {
                    deductionBase = gapEngine.converter.convertToBase(item.requirement.getQuantityNeeded(), item.requirement.getUnit());
                }
                
                double deductionAmount = gapEngine.converter.convertFromBase(deductionBase, pItem.getUnit());
                double newQty = pItem.getQuantity() - deductionAmount;
                
                if (newQty <= 0) pantryRepository.delete(pItem);
                else {
                    pItem.setQuantity(Math.round(newQty * 100.0) / 100.0); 
                    pantryRepository.save(pItem);
                }
            }
        }
        return "redirect:/recipes/" + id + "?cooked=true";
    }


    // UPDATED: Now grabs the existing ingredients and sends them to the form!
    @GetMapping("/{id}/edit")
    public String showEditPage(@PathVariable Long id, Model model) {
        Recipe recipe = getRecipeOr404(id);
        model.addAttribute("recipeForm", recipeService.toForm(recipe));
        model.addAttribute("isEdit", true);
        model.addAttribute("recipeId", id);
        
        // Pass existing ingredients so the UI can draw the rows!
        model.addAttribute("existingIngredients", recipeService.findRecipeIngredients(id));
        
        return "publish";
    }

    @PostMapping("/{id}")
    public String updateRecipe(
            @PathVariable Long id, 
            @ModelAttribute("recipeForm") RecipeForm recipeForm) {
        try {
            // Spring Boot automatically packed the form data for us, so we just pass it to the service!
            Recipe updated = recipeService.updateRecipe(id, recipeForm);
            return "redirect:/recipes/" + updated.getId();
            
        } catch (java.util.NoSuchElementException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Recipe not found", ex);
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, ex.getMessage(), ex);
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

    @PostMapping("/import-url")
    @ResponseBody
    public Map<String, Object> importRecipeFromUrl(@RequestParam("url") String url) {
        try {
            com.sk.smart_kitchen.dto.ScrapedRecipeData scraped = scraperService.scrapeRecipeFromUrl(url);
            String sourceUrl = url != null ? url.trim() : "";
            return Map.of(
                    "title", scraped.getTitle() != null ? scraped.getTitle() : "",
                    "description", scraped.getDescription() != null ? scraped.getDescription() : "",
                    "imageUrl", scraped.getImageUrl() != null ? scraped.getImageUrl() : "",
                    "sourceUrl", sourceUrl,
                    "prepTimeMins", scraped.getPrepTimeMins() != null ? scraped.getPrepTimeMins() : 30,
                    "defaultServings", scraped.getDefaultServings() != null ? scraped.getDefaultServings() : 2,
                    "mealType", scraped.getMealType() != null ? scraped.getMealType() : "Dinner",
                    "ingredients", scraped.getIngredients(),
                    "instructionSteps", scraped.getInstructionSteps()
            );
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
    public ResponseEntity<?> toggleBookmark(@PathVariable Long id, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Recipe recipe = getRecipeOr404(id);

        Optional<SavedRecipe> existing = savedRecipeRepository.findByUserAndRecipe(user, recipe);
        
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

    @PostMapping("/publish")
    public String publishRecipe(
            Recipe recipe,
            @RequestParam("ingredientNames") List<String> ingredientNames,
            @RequestParam("ingredientQuantities") List<Double> ingredientQuantities,
            @RequestParam("ingredientUnits") List<String> ingredientUnits,
            Principal principal) {

        if (principal != null) {
            User author = userRepository.findByEmail(principal.getName()).orElse(null);
            recipe.setAuthor(author);
        }

        List<RecipeIngredient> recipeIngredients = new ArrayList<>();

        for (int i = 0; i < ingredientNames.size(); i++) {
            String name = ingredientNames.get(i);
            if (name == null || name.trim().isEmpty()) continue;

            Ingredient ingredient = new Ingredient();
            ingredient.setName(name);
            ingredient = ingredientRepository.save(ingredient);

            RecipeIngredient ri = new RecipeIngredient();
            ri.setRecipe(recipe);
            ri.setIngredient(ingredient);
            ri.setQuantityNeeded(ingredientQuantities.get(i));
            ri.setUnit(ingredientUnits.get(i));
            
            recipeIngredients.add(ri);
        }

        recipe.setIngredients(recipeIngredients);
        Recipe savedRecipe = recipeRepository.save(recipe);
        return "redirect:/recipes/" + savedRecipe.getId();
    }
}