package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByRecipeIdOrderByIdDesc(Long recipeId);
    
    // CHANGED from Optional<Review> to List<Review>
    List<Review> findByRecipeIdAndUserId(Long recipeId, Long userId);
}