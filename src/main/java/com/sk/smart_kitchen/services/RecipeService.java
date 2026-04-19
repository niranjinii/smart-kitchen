package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.dto.IngredientLineForm;
import com.sk.smart_kitchen.dto.RecipeForm;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import com.sk.smart_kitchen.entities.Tag;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import com.sk.smart_kitchen.repositories.RecipeIngredientRepository;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.TagRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class RecipeService {

    private static final int DEFAULT_INGREDIENT_ROWS = 2;
    private static final int DEFAULT_INSTRUCTION_ROWS = 2;
    private static final Pattern ABSOLUTE_HTTP_URL_PATTERN = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeIngredientRepository recipeIngredientRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final ImageStorageService imageStorageService;

    public RecipeService(
            RecipeRepository recipeRepository,
            IngredientRepository ingredientRepository,
            RecipeIngredientRepository recipeIngredientRepository,
            TagRepository tagRepository,
            UserRepository userRepository,
            ImageStorageService imageStorageService
    ) {
        this.recipeRepository = recipeRepository;
        this.ingredientRepository = ingredientRepository;
        this.recipeIngredientRepository = recipeIngredientRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
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
            line.setQuantity(ingredient.getQuantityNeeded() == null ? "" : stripTrailingZero(ingredient.getQuantityNeeded()));
            line.setUnit(ingredient.getUnit());
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
        Recipe recipe = new Recipe();
        recipe.setAuthor(resolveCurrentUserOrThrow());
        applyFormValues(recipe, form);
        Recipe savedRecipe = recipeRepository.save(recipe);
        upsertIngredients(savedRecipe, form);
        return savedRecipe;
    }

    @Transactional
    public Recipe updateRecipe(Long recipeId, RecipeForm form) {
        validateRequiredFields(form);
        Recipe recipe = findRecipeOrThrow(recipeId);
        applyFormValues(recipe, form);
        Recipe savedRecipe = recipeRepository.save(recipe);

        recipeIngredientRepository.deleteByRecipe(savedRecipe);
        upsertIngredients(savedRecipe, form);
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
        recipeIngredientRepository.deleteByRecipe(recipe);
        recipeRepository.delete(recipe);
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

    private void applyFormValues(Recipe recipe, RecipeForm form) {
        recipe.setTitle(trimToNull(form.getTitle()));
        recipe.setDescription(trimToNull(form.getDescription()));
        recipe.setImageUrl(normalizeImageUrl(form.getImageUrl()));
        recipe.setPrepTimeMins(form.getPrepTimeMins());
        recipe.setDefaultServings(form.getDefaultServings());
        recipe.setMealType(trimToNull(form.getMealType()));
        recipe.setInstructions(joinInstructionSteps(form.getInstructionSteps()));
        recipe.setTags(parseTags(form.getTagInput()));
    }

    private void validateRequiredFields(RecipeForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Recipe form is required.");
        }

        requireNonBlank(form.getTitle(), "Recipe title is required.");
        requireNonBlank(form.getDescription(), "Description is required.");
        if (normalizeImageUrl(form.getImageUrl()) == null) {
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
            String ingredientName = trimToNull(line.getName());
            if (ingredientName == null) {
                continue;
            }

            Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(ingredientName)
                    .orElseGet(() -> {
                        Ingredient newIngredient = new Ingredient();
                        newIngredient.setName(ingredientName);
                        newIngredient.setMeasurementUnit(trimToNull(line.getUnit()));
                        return ingredientRepository.save(newIngredient);
                    });

            if (ingredient.getMeasurementUnit() == null && trimToNull(line.getUnit()) != null) {
                ingredient.setMeasurementUnit(trimToNull(line.getUnit()));
                ingredientRepository.save(ingredient);
            }

            RecipeIngredient recipeIngredient = new RecipeIngredient();
            recipeIngredient.setRecipe(recipe);
            recipeIngredient.setIngredient(ingredient);
            recipeIngredient.setQuantityNeeded(parseQuantity(line.getQuantity()));
            recipeIngredient.setUnit(trimToNull(line.getUnit()));
            recipeIngredientRepository.save(recipeIngredient);
        }
    }

    private Set<Tag> parseTags(String tagInput) {
        if (tagInput == null || tagInput.isBlank()) {
            return new LinkedHashSet<>();
        }

        String[] parts = tagInput.split(",");
        Set<Tag> tags = new LinkedHashSet<>();

        for (String rawPart : parts) {
            String normalizedTag = normalizeTag(rawPart);
            if (normalizedTag == null) {
                continue;
            }

            Tag tag = tagRepository.findByNameIgnoreCase(normalizedTag)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(normalizedTag);
                        return tagRepository.save(newTag);
                    });
            tags.add(tag);
        }

        return tags;
    }

    private String joinInstructionSteps(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            return null;
        }

        List<String> nonBlankSteps = steps.stream()
                .map(this::trimToNull)
                .filter(Objects::nonNull)
                .toList();

        if (nonBlankSteps.isEmpty()) {
            return null;
        }

        return String.join("\n", nonBlankSteps);
    }

    private void ensureMinimumRows(RecipeForm form) {
        if (form.getIngredients() == null) {
            form.setIngredients(new ArrayList<>());
        }
        while (form.getIngredients().size() < DEFAULT_INGREDIENT_ROWS) {
            form.getIngredients().add(new IngredientLineForm());
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

   private String normalizeTag(String rawTag) {
    String tag = trimToNull(rawTag);
    if (tag == null) {
        return null;
    }

    // Scrunches any accidental double/triple spaces inside the text into a single space
    tag = tag.replaceAll("\\s+", " ");

    // Existing logic
    return tag.toLowerCase(Locale.ROOT).substring(0, 1).toUpperCase(Locale.ROOT) + 
           tag.toLowerCase(Locale.ROOT).substring(1);
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

    private String normalizeImageUrl(String imageUrl) {
        String cleaned = trimToNull(imageUrl);
        if (cleaned == null) {
            return null;
        }

        String normalized = cleaned.replace('\\', '/');
        if (normalized.startsWith("file:")) {
            return null;
        }
        if (normalized.matches("^[A-Za-z]:/.*")) {
            return null;
        }
        if (ABSOLUTE_HTTP_URL_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }

        while (normalized.startsWith("//")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized;
    }
}
