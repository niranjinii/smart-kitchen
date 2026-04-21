package com.sk.smart_kitchen.entities;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "recipes")
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(columnDefinition = "TEXT") // Allows for very long cooking instructions
    private String instructions;

    @Column(length = 1000) // URLs can be long
    private String imageUrl;

    @Column(length = 1000)
    private String importSourceUrl;

    private Integer prepTimeMins;
    
    private String mealType; // "Breakfast", "Lunch", "Dinner", "Snack"

    @Column(name = "default_servings")
    private Integer defaultServings;

    @ManyToOne
    @JoinColumn(name = "user_id") // This creates the foreign key column in the DB
    private User author;

    // This creates the hidden "recipe_tags" bridge table
    @ManyToMany
    @JoinTable(
        name = "recipe_tags", 
        joinColumns = @JoinColumn(name = "recipe_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecipeIngredient> recipeIngredients = new ArrayList<>();


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImportSourceUrl() {
        return importSourceUrl;
    }

    public void setImportSourceUrl(String importSourceUrl) {
        this.importSourceUrl = importSourceUrl;
    }

    public Integer getPrepTimeMins() {
        return prepTimeMins;
    }

    public void setPrepTimeMins(Integer prepTimeMins) {
        this.prepTimeMins = prepTimeMins;
    }

    public String getMealType() {
        return mealType;
    }

    public void setMealType(String mealType) {
        this.mealType = mealType;
    }

    public Set<Tag> getTags() {
        return tags;
    }

    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public Integer getDefaultServings() {
        return defaultServings;
    }

    public void setDefaultServings(Integer defaultServings) {
        this.defaultServings = defaultServings;
    }

    public List<RecipeIngredient> getRecipeIngredients() {
        return recipeIngredients;
    }

    public void setRecipeIngredients(List<RecipeIngredient> recipeIngredients) {
        this.recipeIngredients = recipeIngredients;
    }

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<RecipeIngredient> ingredients = new java.util.ArrayList<>();

    // Getter
    public java.util.List<RecipeIngredient> getIngredients() {
        return ingredients;
    }

    // Setter
    public void setIngredients(java.util.List<RecipeIngredient> ingredients) {
        this.ingredients = ingredients;
    }

}
