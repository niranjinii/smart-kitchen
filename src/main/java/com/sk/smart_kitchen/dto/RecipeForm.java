package com.sk.smart_kitchen.dto;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public class RecipeForm {
    private String title;
    private String description;
    private String imageUrl;
    private String importSourceUrl;
    private Integer prepTimeMins;
    private Integer defaultServings;
    private String mealType;
    private MultipartFile imageFile;
    private String tagInput;
    private List<IngredientLineForm> ingredients = new ArrayList<>();
    private List<String> instructionSteps = new ArrayList<>();
    
    // 🌟 NEW: Holds the explicit author suggestions
    private List<SubstitutionForm> explicitSubs = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getImportSourceUrl() { return importSourceUrl; }
    public void setImportSourceUrl(String importSourceUrl) { this.importSourceUrl = importSourceUrl; }

    public Integer getPrepTimeMins() { return prepTimeMins; }
    public void setPrepTimeMins(Integer prepTimeMins) { this.prepTimeMins = prepTimeMins; }

    public Integer getDefaultServings() { return defaultServings; }
    public void setDefaultServings(Integer defaultServings) { this.defaultServings = defaultServings; }

    public String getMealType() { return mealType; }
    public void setMealType(String mealType) { this.mealType = mealType; }

    public String getTagInput() { return tagInput; }
    public void setTagInput(String tagInput) { this.tagInput = tagInput; }

    public List<IngredientLineForm> getIngredients() { return ingredients; }
    public void setIngredients(List<IngredientLineForm> ingredients) { this.ingredients = ingredients; }

    public List<String> getInstructionSteps() { return instructionSteps; }
    public void setInstructionSteps(List<String> instructionSteps) { this.instructionSteps = instructionSteps; }

    public MultipartFile getImageFile() { return imageFile; }
    public void setImageFile(MultipartFile imageFile) { this.imageFile = imageFile; }

    public List<SubstitutionForm> getExplicitSubs() { return explicitSubs; }
    public void setExplicitSubs(List<SubstitutionForm> explicitSubs) { this.explicitSubs = explicitSubs; }
}