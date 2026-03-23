package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    // JpaRepository gives us findAll(), save(), and findById() for free!
}
