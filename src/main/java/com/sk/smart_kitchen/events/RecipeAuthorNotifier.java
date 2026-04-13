package com.sk.smart_kitchen.events;

import com.sk.smart_kitchen.entities.Review;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class RecipeAuthorNotifier implements ApplicationListener<ReviewCreatedEvent> {

    // This method automatically runs whenever a ReviewCreatedEvent is published!
    @Override
    public void onApplicationEvent(ReviewCreatedEvent event) {
        Review review = event.getReview();
        String authorEmail = review.getRecipe().getAuthor().getEmail();
        String recipeName = review.getRecipe().getTitle();
        
        // In a real app, you would send an email here. For the project, a print statement proves the pattern works!
        System.out.println("=====================================================");
        System.out.println("OBSERVER TRIGGERED: Sending email to " + authorEmail);
        System.out.println("Message: Someone left a " + review.getRating() + "-star review on '" + recipeName + "'");
        System.out.println("=====================================================");
    }
}