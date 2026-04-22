package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    // JpaRepository gives us findAll(), save(), and findById() for free!
    @EntityGraph(attributePaths = {"tags", "author"})
    Optional<Recipe> findWithTagsAndAuthorById(Long id);
    // ADD THIS INSIDE RecipeRepository.java
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM Recipe r LEFT JOIN FETCH r.recipeIngredients ri LEFT JOIN FETCH ri.ingredient")
    java.util.List<com.sk.smart_kitchen.entities.Recipe> findAllWithIngredients();

    java.util.List<Recipe> findByAuthorOrderByIdDesc(User author);
}
