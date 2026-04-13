package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Review;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.events.ReviewCreatedEvent;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.ReviewRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class CommunityController {

    @Autowired
    private ReviewRepository reviewRepository;
    
    @Autowired
    private RecipeRepository recipeRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PostMapping("/reviews/add")
    public String addReview(Review review, Principal principal, @RequestParam Long recipeId) {
        // 1. Get the currently logged-in user (Principal holds the logged-in user's email/username)
        User currentUser = userRepository.findByEmail(principal.getName()).orElseThrow();
        review.setUser(currentUser);
        
        // 2. Link the review to the correct recipe
        review.setRecipe(recipeRepository.findById(recipeId).orElseThrow());
        
        // 3. Save to database
        reviewRepository.save(review);
        
        // 4. Fire your OOAD Observer Pattern Event!
        eventPublisher.publishEvent(new ReviewCreatedEvent(this, review));
        
        // 5. Redirect back to the recipe page they were just on
        return "redirect:/recipes/" + recipeId;
    }
}