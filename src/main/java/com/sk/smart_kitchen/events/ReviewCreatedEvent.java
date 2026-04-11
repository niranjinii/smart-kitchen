package com.sk.smart_kitchen.events;

import com.sk.smart_kitchen.entities.Review;
import org.springframework.context.ApplicationEvent;

// This is the message that gets broadcasted
public class ReviewCreatedEvent extends ApplicationEvent {
    private final Review review;

    public ReviewCreatedEvent(Object source, Review review) {
        super(source);
        this.review = review;
    }

    public Review getReview() {
        return review;
    }
}