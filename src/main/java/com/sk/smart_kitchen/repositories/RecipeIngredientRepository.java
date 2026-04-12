package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.RecipeIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeIngredientRepository extends JpaRepository<RecipeIngredient, Long> {
    List<RecipeIngredient> findByRecipeIdOrderByIdAsc(Long recipeId);

    void deleteByRecipe(Recipe recipe);
}
