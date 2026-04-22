package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.dto.IngredientLineForm;
import com.sk.smart_kitchen.dto.RecipeCreationRequest;
import com.sk.smart_kitchen.dto.RecipeForm;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import com.sk.smart_kitchen.entities.Tag;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import com.sk.smart_kitchen.repositories.RecipeIngredientRepository;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
public class RecipeService {

    private static final int DEFAULT_INGREDIENT_ROWS = 2;
    private static final int DEFAULT_INSTRUCTION_ROWS = 2;

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;
    private final IngredientNormalizer normalizer;
    private final RecipeFactory recipeFactory;

    public RecipeService(
            RecipeRepository recipeRepository,
            IngredientRepository ingredientRepository,
            RecipeIngredientRepository recipeIngredientRepository,
            UserRepository userRepository,
            ImageStorageService imageStorageService,
            IngredientNormalizer normalizer,
            RecipeFactory recipeFactory
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
        this.normalizer = normalizer;
        this.recipeFactory = recipeFactory;
    }

    public List<Recipe> findAllRecipes() {
        return recipeRepository.findAll();
    }

    public Recipe findRecipeOrThrow(Long id) {
        return recipeRepository.findWithTagsAndAuthorById(id)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found"));
    }

    public List<RecipeIngredient> findRecipeIngredients(Long recipeId) {
        return recipeIngredientRepository.findByRecipeIdOrderByIdAsc(recipeId);
    }

    public RecipeForm emptyForm() {
        RecipeForm form = new RecipeForm();
        ensureMinimumRows(form);
        return form;
    }

    public RecipeForm toForm(Recipe recipe) {
        RecipeForm form = new RecipeForm();
        form.setTitle(recipe.getTitle());
        form.setDescription(recipe.getDescription());
        form.setImageUrl(recipe.getImageUrl());
        form.setImportSourceUrl(recipe.getImportSourceUrl());
        form.setPrepTimeMins(recipe.getPrepTimeMins());
        form.setDefaultServings(recipe.getDefaultServings());
        form.setMealType(recipe.getMealType());

        Set<Tag> tags = recipe.getTags();
        if (tags != null && !tags.isEmpty()) {
            String tagInput = tags.stream()
                    .map(Tag::getName)
                    .filter(Objects::nonNull)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");
            form.setTagInput(tagInput);
        }

        List<RecipeIngredient> ingredients = findRecipeIngredients(recipe.getId());
        List<IngredientLineForm> ingredientLines = new ArrayList<>();
        for (RecipeIngredient ingredient : ingredients) {
            IngredientLineForm line = new IngredientLineForm();
            line.setName(ingredient.getIngredient().getName());
            line.setQuantity(ingredient.getQuantityNeeded() != null ? stripTrailingZero(ingredient.getQuantityNeeded()) : "0");
            line.setUnit(ingredient.getUnit());
            line.setPreparation(ingredient.getPreparationState()); // Correctly mapped
            ingredientLines.add(line);
        }
        form.setIngredients(ingredientLines);

        if (recipe.getInstructions() != null && !recipe.getInstructions().isBlank()) {
            form.setInstructionSteps(new ArrayList<>(Arrays.asList(recipe.getInstructions().split("\\r?\\n"))));
        }

        ensureMinimumRows(form);
        return form;
    }

    @Transactional
    public Recipe createRecipe(RecipeForm form) {
        validateRequiredFields(form);
        Recipe recipe = recipeFactory.create(RecipeCreationRequest.fromForm(form));
        recipe.setAuthor(resolveCurrentUserOrThrow());
        Recipe savedRecipe = recipeRepository.save(recipe);
        upsertIngredients(savedRecipe, form);
        return savedRecipe;
    }

    @Transactional
    public Recipe updateRecipe(Long recipeId, RecipeForm form) {
        validateRequiredFields(form);
        Recipe recipe = findRecipeOrThrow(recipeId);
        
        // 1. Save the OLD URL before we apply the new form values
        String oldImageUrl = recipe.getImageUrl(); 
        
        recipeFactory.populate(recipe, RecipeCreationRequest.fromForm(form));
        Recipe savedRecipe = recipeRepository.save(recipe);

        recipeIngredientRepository.deleteByRecipe(savedRecipe);
        upsertIngredients(savedRecipe, form);

        // 2. CLEANUP: If the URL changed, scrub the old image from the cloud!
        if (oldImageUrl != null && !oldImageUrl.equals(savedRecipe.getImageUrl())) {
            imageStorageService.deleteImageFromCloudinary(oldImageUrl);
        }

        return savedRecipe;
    }

    @Transactional
    public void deleteRecipe(Long recipeId) {
        User currentUser = resolveCurrentUserOrThrow();
        Recipe recipe = findRecipeOrThrow(recipeId);
        if (recipe.getAuthor() == null || recipe.getAuthor().getId() == null
                || !recipe.getAuthor().getId().equals(currentUser.getId())) {
            throw new SecurityException("You can only delete your own recipes.");
        }
        
        // 1. Save the URL before the recipe goes poof
        String oldImageUrl = recipe.getImageUrl();
        
        recipeIngredientRepository.deleteByRecipe(recipe);
        recipeRepository.delete(recipe);
        
        // 2. CLEANUP: Delete the actual file from the cloud!
        if (oldImageUrl != null) {
            imageStorageService.deleteImageFromCloudinary(oldImageUrl);
        }
    }

    public String storeRecipeImage(MultipartFile imageFile) {
        return imageStorageService.storeImage(imageFile);
    }

    public List<String> toInstructionSteps(Recipe recipe) {
        if (recipe.getInstructions() == null || recipe.getInstructions().isBlank()) {
            return List.of();
        }
        return Arrays.stream(recipe.getInstructions().split("\\r?\\n"))
                .map(String::trim)
                .filter(step -> !step.isBlank())
                .toList();
    }

    private void validateRequiredFields(RecipeForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Recipe form is required.");
        }

        requireNonBlank(form.getTitle(), "Recipe title is required.");
        requireNonBlank(form.getDescription(), "Description is required.");
        if (recipeFactory.normalizeImageUrl(form.getImageUrl()) == null) {
            throw new IllegalArgumentException("Recipe image URL is required.");
        }
        if (form.getPrepTimeMins() == null || form.getPrepTimeMins() < 1) {
            throw new IllegalArgumentException("Total time must be at least 1 minute.");
        }
        if (form.getDefaultServings() == null || form.getDefaultServings() < 1) {
            throw new IllegalArgumentException("Base yield must be at least 1 serving.");
        }
        requireNonBlank(form.getMealType(), "Primary category is required.");

        validateIngredients(form.getIngredients());
        validateInstructionSteps(form.getInstructionSteps());
    }

    private void validateIngredients(List<IngredientLineForm> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            throw new IllegalArgumentException("At least one ingredient is required.");
        }

        int completeRows = 0;
        for (int i = 0; i < ingredients.size(); i++) {
            IngredientLineForm line = ingredients.get(i);
            int row = i + 1;
            if (line == null) {
                continue;
            }

            String name = trimToNull(line.getName());
            String unit = trimToNull(line.getUnit());
            String quantity = trimToNull(line.getQuantity());
            boolean hasAnyField = name != null || unit != null || quantity != null;

            if (!hasAnyField) {
                continue;
            }

            if (name == null || quantity == null) {
                throw new IllegalArgumentException("Ingredient row " + row + " is incomplete. Add name, unit, and quantity.");
            }

            completeRows++;

            double parsedQuantity;
            try {
                parsedQuantity = Double.parseDouble(quantity);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Ingredient quantity must be a number in row " + row + ".", ex);
            }
            if (parsedQuantity <= 0) {
                throw new IllegalArgumentException("Ingredient quantity must be greater than 0 in row " + row + ".");
            }
        }

        if (completeRows < 1) {
            throw new IllegalArgumentException("Please add at least one ingredient.");
        }
    }

    private void validateInstructionSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalArgumentException("At least one instruction step is required.");
        }

        int nonBlankSteps = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (trimToNull(steps.get(i)) != null) {
                nonBlankSteps++;
            }
        }

        if (nonBlankSteps < 1) {
            throw new IllegalArgumentException("Please add at least one instruction step.");
        }
    }

    private void requireNonBlank(String value, String message) {
        if (trimToNull(value) == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private User resolveCurrentUserOrThrow() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new SecurityException("You must be logged in to perform this action.");
        }

        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new SecurityException("Authenticated user account was not found."));
    }

    private void upsertIngredients(Recipe recipe, RecipeForm form) {
        if (form.getIngredients() == null) {
            return;
        }

        for (IngredientLineForm line : form.getIngredients()) {
            String rawIngredientName = trimToNull(line.getName());
            if (rawIngredientName == null) continue;

            // 🌟 PASS IT THROUGH THE NORMALIZER
            String cleanIngredientName = normalizer.normalize(rawIngredientName);
            if (cleanIngredientName == null) continue;

            // 1. Get or create the dumb master ingredient using the CLEAN name
            Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(cleanIngredientName)
                    .orElseGet(() -> {
                        Ingredient newIngredient = new Ingredient();
                        newIngredient.setName(cleanIngredientName);
                        return ingredientRepository.save(newIngredient);
                    });

            // 2. Save all the specifics to the connecting table (RecipeIngredient)
            RecipeIngredient recipeIngredient = new RecipeIngredient();
            recipeIngredient.setRecipe(recipe);
            recipeIngredient.setIngredient(ingredient);
            recipeIngredient.setQuantityNeeded(parseQuantity(line.getQuantity()));
            recipeIngredient.setUnit(trimToNull(line.getUnit()));
            recipeIngredient.setPreparationState(trimToNull(line.getPreparation())); 
            recipeIngredientRepository.save(recipeIngredient);
        }
    }
    
    private void ensureMinimumRows(RecipeForm form) {
        if (form.getIngredients() == null) {
            form.setIngredients(new ArrayList<>());
        }
        while (form.getIngredients().size() < DEFAULT_INGREDIENT_ROWS) {
            IngredientLineForm newLine = new IngredientLineForm();
            newLine.setQuantity("");
            form.getIngredients().add(newLine);
        }

        if (form.getInstructionSteps() == null) {
            form.setInstructionSteps(new ArrayList<>());
        }
        while (form.getInstructionSteps().size() < DEFAULT_INSTRUCTION_ROWS) {
            form.getInstructionSteps().add("");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double parseQuantity(String value) {
        try {
            String cleaned = trimToNull(value);
            if (cleaned == null) {
                return 0.0;
            }
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String stripTrailingZero(Double value) {
        if (value == null) {
            return "";
        }
        if (value % 1 == 0) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }

}