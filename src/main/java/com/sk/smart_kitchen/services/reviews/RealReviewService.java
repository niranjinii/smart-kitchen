package com.sk.smart_kitchen.services.reviews;

import com.sk.smart_kitchen.entities.Review;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.ReviewRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List; // Needed for the cleanup list!

@Service("realReviewService")
public class RealReviewService implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    public RealReviewService(ReviewRepository reviewRepository, RecipeRepository recipeRepository, UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.recipeRepository = recipeRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void saveReview(Review review, Long recipeId, String userEmail) {
        User currentUser = userRepository.findByEmail(userEmail).orElseThrow();
        
        // 🚨 GRACEFUL CLEANUP: Find existing reviews by this user and delete them!
        List<Review> existingReviews = reviewRepository.findByRecipeIdAndUserId(recipeId, currentUser.getId());
        if (!existingReviews.isEmpty()) {
            reviewRepository.deleteAll(existingReviews);
        }

        review.setUser(currentUser);
        review.setRecipe(recipeRepository.findById(recipeId).orElseThrow());
        
        reviewRepository.save(review);
    }
}