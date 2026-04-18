package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Review;
import com.sk.smart_kitchen.entities.ChefsNote;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.ChefsNoteRepository;
import com.sk.smart_kitchen.repositories.ReviewRepository; // <-- Added this import
import com.sk.smart_kitchen.services.reviews.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
public class CommunityController {

    private final ReviewService reviewService;

    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RecipeRepository recipeRepository;
    
    @Autowired
    private ChefsNoteRepository chefsNoteRepository;

    @Autowired // <-- Added the repository for deleting reviews
    private ReviewRepository reviewRepository;

    // We explicitly tell Spring to inject the PROXY, not the real one!
    public CommunityController(@Qualifier("reviewServiceProxy") ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // --- 1. REVIEWS (Using your Proxy Pattern) ---
    @PostMapping("/reviews/add")
    public String addReview(Review review, Principal principal, @RequestParam Long recipeId) {
        // The controller doesn't know it's talking to a proxy!
        reviewService.saveReview(review, recipeId, principal.getName());
        return "redirect:/recipes/" + recipeId;
    }

    // --- NEW: DELETE REVIEW ENDPOINT ---
    @PostMapping("/reviews/delete")
    public String deleteReview(@RequestParam Long reviewId, @RequestParam Long recipeId, Principal principal) {
        // Safe Delete: Checks the database to make sure the logged-in user actually owns the review
        reviewRepository.findById(reviewId).ifPresent(review -> {
            if (review.getUser().getEmail().equals(principal.getName())) {
                reviewRepository.delete(review);
            }
        });
        return "redirect:/recipes/" + recipeId;
    }

    // --- 2. CHEF'S NOTES (Crash Fixed!) ---
    @PostMapping("/notes/add")
    public String addNote(@RequestParam Long recipeId, @RequestParam String content, Principal principal) {
        if (principal == null) return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Recipe recipe = recipeRepository.findById(recipeId).orElseThrow();

        // Find existing note OR create a new one if it doesn't exist
        ChefsNote note = chefsNoteRepository.findFirstByUserAndRecipe(user, recipe).orElse(new ChefsNote());
        
        note.setUser(user);
        note.setRecipe(recipe);
        note.setContent(content); // Overwrite old text with new text
        chefsNoteRepository.save(note); 

        return "redirect:/recipes/" + recipeId;
    }
}