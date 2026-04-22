package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.dto.RecipeCreationRequest;
import com.sk.smart_kitchen.dto.RecipeForm;
import com.sk.smart_kitchen.dto.RecipeSourceType;
import com.sk.smart_kitchen.dto.ScrapedRecipeData;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.Tag;
import com.sk.smart_kitchen.repositories.TagRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class RecipeFactory {

    private static final Pattern ABSOLUTE_HTTP_URL_PATTERN = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);

    private final TagRepository tagRepository;

    public RecipeFactory(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    public Recipe create(RecipeCreationRequest request) {
        Recipe recipe = new Recipe();
        populate(recipe, request);
        return recipe;
    }

    public void populate(Recipe recipe, RecipeCreationRequest request) {
        if (recipe == null) {
            throw new IllegalArgumentException("Recipe target is required.");
        }
        if (request == null || request.getSourceType() == null) {
            throw new IllegalArgumentException("Recipe source request is required.");
        }

        if (request.getSourceType() == RecipeSourceType.FORM) {
            applyFromForm(recipe, request.getRecipeForm());
            return;
        }

        if (request.getSourceType() == RecipeSourceType.SCRAPED_URL) {
            applyFromScraped(recipe, request.getScrapedRecipeData(), request.getSourceUrl());
            return;
        }

        throw new IllegalArgumentException("Unsupported recipe source type.");
    }

    public String normalizeImageUrl(String imageUrl) {
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

    private void applyFromForm(Recipe recipe, RecipeForm form) {
        if (form == null) {
            throw new IllegalArgumentException("Recipe form is required.");
        }

        recipe.setTitle(trimToNull(form.getTitle()));
        recipe.setDescription(trimToNull(form.getDescription()));
        recipe.setImageUrl(normalizeImageUrl(form.getImageUrl()));
        recipe.setImportSourceUrl(normalizeImportSourceUrl(form.getImportSourceUrl()));
        recipe.setPrepTimeMins(form.getPrepTimeMins());
        recipe.setDefaultServings(form.getDefaultServings());
        recipe.setMealType(trimToNull(form.getMealType()));
        recipe.setInstructions(joinInstructionSteps(form.getInstructionSteps()));
        recipe.setTags(parseTags(form.getTagInput()));
    }

    private void applyFromScraped(Recipe recipe, ScrapedRecipeData data, String sourceUrl) {
        if (data == null) {
            throw new IllegalArgumentException("Scraped recipe payload is required.");
        }

        recipe.setTitle(trimToNull(data.getTitle()));
        recipe.setDescription(trimToNull(data.getDescription()));
        recipe.setImageUrl(normalizeImageUrl(data.getImageUrl()));
        recipe.setImportSourceUrl(normalizeImportSourceUrl(sourceUrl));
        recipe.setPrepTimeMins(data.getPrepTimeMins());
        recipe.setDefaultServings(data.getDefaultServings());
        recipe.setMealType(trimToNull(data.getMealType()));
        recipe.setInstructions(joinInstructionSteps(data.getInstructionSteps()));
        recipe.setTags(new LinkedHashSet<>());
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

    private String normalizeTag(String rawTag) {
        String tag = trimToNull(rawTag);
        if (tag == null) {
            return null;
        }

        tag = tag.replaceAll("\\s+", " ");
        return tag.toLowerCase(Locale.ROOT).substring(0, 1).toUpperCase(Locale.ROOT) +
                tag.toLowerCase(Locale.ROOT).substring(1);
    }

    private String normalizeImportSourceUrl(String importSourceUrl) {
        String cleaned = trimToNull(importSourceUrl);
        if (cleaned == null) {
            return null;
        }

        String normalized = cleaned.replace('\\', '/');
        return ABSOLUTE_HTTP_URL_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
