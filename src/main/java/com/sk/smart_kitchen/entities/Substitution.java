package com.sk.smart_kitchen.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "substitutions")
public class Substitution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "original_ingredient_id", nullable = false)
    private Ingredient originalIngredient;

    @ManyToOne
    @JoinColumn(name = "substitute_ingredient_id", nullable = false)
    private Ingredient substituteIngredient;

    private Double conversionMultiplier; // e.g., 1.5x the amount
    private String notes; // e.g., "Mix with 1 tsp lemon juice"
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Ingredient getOriginalIngredient() {
        return originalIngredient;
    }
    public void setOriginalIngredient(Ingredient originalIngredient) {
        this.originalIngredient = originalIngredient;
    }
    public Ingredient getSubstituteIngredient() {
        return substituteIngredient;
    }
    public void setSubstituteIngredient(Ingredient substituteIngredient) {
        this.substituteIngredient = substituteIngredient;
    }
    public Double getConversionMultiplier() {
        return conversionMultiplier;
    }
    public void setConversionMultiplier(Double conversionMultiplier) {
        this.conversionMultiplier = conversionMultiplier;
    }
    public String getNotes() {
        return notes;
    }
    public void setNotes(String notes) {
        this.notes = notes;
    }

    
}