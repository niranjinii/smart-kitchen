package com.sk.smart_kitchen.services;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IngredientNormalizer {

    // 1. The Adjective Blacklist (Fluff Words)
    private static final Set<String> FLUFF_WORDS = Set.of(
            "large", "medium", "small", "extra", "fresh", "organic", "raw", "whole", "jumbo"
    );

    // 2. The Untouchables (Words that naturally end in 's' and shouldn't be chopped)
    private static final Set<String> UNTOUCHABLES = Set.of(
            "asparagus", "molasses", "lemongrass", "couscous", "hummus", "watercress"
    );

    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;

        // Strip out non-letters and convert to lowercase
        String cleaned = rawName.toLowerCase().replaceAll("[^a-z\\s]", "").trim();

        // Remove the fluff adjectives
        cleaned = Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !FLUFF_WORDS.contains(word))
                .collect(Collectors.joining(" "));

        if (cleaned.isBlank()) return null;

        // Skip stemming if it's a special word
        if (UNTOUCHABLES.contains(cleaned)) {
            return capitalizeWords(cleaned);
        }

        // 3. The Lightweight Stemmer (De-pluralizer)
        if (cleaned.endsWith("oes") && cleaned.length() > 4) { 
            // tomatoes -> tomato
            cleaned = cleaned.substring(0, cleaned.length() - 2);
        } else if (cleaned.endsWith("ies") && cleaned.length() > 4) { 
            // berries -> berry
            cleaned = cleaned.substring(0, cleaned.length() - 3) + "y";
        } else if (cleaned.endsWith("s") && !cleaned.endsWith("ss") && cleaned.length() > 3) {
            // eggs -> egg, carrots -> carrot
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return capitalizeWords(cleaned);
    }

    // Helper to make it look pretty again ("red onion" -> "Red Onion")
    private String capitalizeWords(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(word -> word.isEmpty() ? "" : word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}