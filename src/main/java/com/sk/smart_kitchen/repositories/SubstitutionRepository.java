package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.Substitution;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubstitutionRepository extends JpaRepository<Substitution, Long> {

    // 1. Needed by the RecipeService auto-slicer
    Optional<Substitution> findByOriginalIngredientAndSubstituteIngredient(Ingredient original, Ingredient substitute);

    // 2. Needed by the GapAnalysisEngine (General search)
    List<Substitution> findByOriginalIngredient(Ingredient original);

    // 3. Needed by the GapAnalysisEngine (User-scoped personal swaps)
    Optional<Substitution> findByOriginalIngredientAndUser(Ingredient original, User user);

    // 4. Needed by the GapAnalysisEngine (Recipe-scoped author suggestions)
    List<Substitution> findByOriginalIngredientAndRecipe(Ingredient original, Recipe recipe);

    // 5. Needed by RecipeService to clean up old suggestions when editing a recipe
    void deleteByRecipe(Recipe recipe);
}