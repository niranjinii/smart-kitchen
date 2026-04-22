package com.sk.smart_kitchen.dto;

public class RecipeCreationRequest {
    private final RecipeSourceType sourceType;
    private final RecipeForm recipeForm;
    private final ScrapedRecipeData scrapedRecipeData;
    private final String sourceUrl;

    private RecipeCreationRequest(
            RecipeSourceType sourceType,
            RecipeForm recipeForm,
            ScrapedRecipeData scrapedRecipeData,
            String sourceUrl
    ) {
        this.sourceType = sourceType;
        this.recipeForm = recipeForm;
        this.scrapedRecipeData = scrapedRecipeData;
        this.sourceUrl = sourceUrl;
    }

    public static RecipeCreationRequest fromForm(RecipeForm recipeForm) {
        return new RecipeCreationRequest(RecipeSourceType.FORM, recipeForm, null, null);
    }

    public static RecipeCreationRequest fromScraped(ScrapedRecipeData scrapedRecipeData, String sourceUrl) {
        return new RecipeCreationRequest(RecipeSourceType.SCRAPED_URL, null, scrapedRecipeData, sourceUrl);
    }

    public RecipeSourceType getSourceType() {
        return sourceType;
    }

    public RecipeForm getRecipeForm() {
        return recipeForm;
    }

    public ScrapedRecipeData getScrapedRecipeData() {
        return scrapedRecipeData;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }
}
