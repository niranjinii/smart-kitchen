package com.sk.smart_kitchen.services.reviews;

import com.sk.smart_kitchen.entities.Review;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service("reviewServiceProxy")
public class ReviewServiceProxy implements ReviewService {

    private final ReviewService realReviewService;

    // Notice we inject the REAL service into the Proxy
    public ReviewServiceProxy(@Qualifier("realReviewService") ReviewService realReviewService) {
        this.realReviewService = realReviewService;
    }

    @Override
    public void saveReview(Review review, Long recipeId, String userEmail) {
        // 1. Let the real service do the boring database work
        realReviewService.saveReview(review, recipeId, userEmail);

        // 2. The Proxy adds the extra functionality!
        notifyAuthor(review);
    }

    private void notifyAuthor(Review review) {
        String authorEmail = review.getRecipe().getAuthor().getEmail();
        String recipeName = review.getRecipe().getTitle();
        
        System.out.println("=====================================================");
        System.out.println("PROXY TRIGGERED: Sending email to " + authorEmail);
        System.out.println("Message: Someone left a " + review.getRating() + "-star review on '" + recipeName + "'");
        System.out.println("=====================================================");
    }
}