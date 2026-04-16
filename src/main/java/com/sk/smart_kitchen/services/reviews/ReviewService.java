package com.sk.smart_kitchen.services.reviews;

import com.sk.smart_kitchen.entities.Review;

public interface ReviewService {
    void saveReview(Review review, Long recipeId, String userEmail);
}