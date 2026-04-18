package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.SavedRecipe;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface SavedRecipeRepository extends JpaRepository<SavedRecipe, Long> {
    Optional<SavedRecipe> findByUserAndRecipe(User user, Recipe recipe);
    List<SavedRecipe> findByUser(User user); // NEW: To fetch all your saved recipes!
}