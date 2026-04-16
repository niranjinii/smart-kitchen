package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Review;
import com.sk.smart_kitchen.services.reviews.ReviewService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class CommunityController {

    private final ReviewService reviewService;

    // We explicitly tell Spring to inject the PROXY, not the real one!
    public CommunityController(@Qualifier("reviewServiceProxy") ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/reviews/add")
    public String addReview(Review review, Principal principal, @RequestParam Long recipeId) {
        // The controller doesn't know it's talking to a proxy!
        reviewService.saveReview(review, recipeId, principal.getName());
        return "redirect:/recipes/" + recipeId;
    }
}