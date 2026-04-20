package com.sk.smart_kitchen.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "shopping_list_items")
public class ShoppingListItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // FIXED: Now properly maps to the ingredient_id in your database!
    @ManyToOne
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    private double quantityNeeded;
    private String unit;
    private boolean isChecked = false;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }
    public double getQuantityNeeded() { return quantityNeeded; }
    public void setQuantityNeeded(double quantityNeeded) { this.quantityNeeded = quantityNeeded; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean getIsChecked() { return isChecked; }
    public void setIsChecked(boolean checked) { isChecked = checked; }
}