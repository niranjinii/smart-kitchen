package com.sk.smart_kitchen.services;

import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IngredientNormalizer {

    // The Upgraded Ontology Dictionary
    private static final Set<String> FLUFF_WORDS = Set.of(
            // Sizes & States
            "large", "medium", "small", "extra", "fresh", "organic", "raw", "whole", "jumbo",
            
            // Dairy & Fat variations
            "skimmed", "skim", "low-fat", "full-fat", "non-fat", "unsalted", "salted",
            
            // Preparations
            "shredded", "grated", "diced", "chopped", "minced", "sliced", "peeled", "crushed", "melted"
    );

    private static final Set<String> UNTOUCHABLES = Set.of(
            "asparagus", "molasses", "lemongrass", "couscous", "hummus", "watercress"
    );

    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;

        // 1. Vaporize recipe fluff phrases and punctuation (Your custom logic)
        String cleaned = rawName.toLowerCase()
                .replaceAll("to taste", "")
                .replaceAll("just a splash", "")
                .replaceAll("a splash", "")
                .replaceAll("[^a-z\\s]", "")
                .trim();

        // 2. Filter out the single fluff words
        cleaned = Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !FLUFF_WORDS.contains(word))
                .collect(Collectors.joining(" "));

        // 3. Fallback: If they literally only typed "fresh", don't return blank
        if (cleaned.isBlank()) {
             return capitalizeWords(rawName.replaceAll("[^a-zA-Z\\s]", "").trim());
        }

        // 4. Untouchables bypass (Your custom logic)
        if (UNTOUCHABLES.contains(cleaned)) return capitalizeWords(cleaned);

        // 5. De-pluralization engine (Your custom logic)
        if (cleaned.endsWith("oes") && cleaned.length() > 4) { 
            cleaned = cleaned.substring(0, cleaned.length() - 2);
        } else if (cleaned.endsWith("ies") && cleaned.length() > 4) { 
            cleaned = cleaned.substring(0, cleaned.length() - 3) + "y";
        } else if (cleaned.endsWith("s") && !cleaned.endsWith("ss") && cleaned.length() > 3) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }

        return capitalizeWords(cleaned);
    }

    // Capitalizer
    private String capitalizeWords(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(word -> word.isEmpty() ? "" : word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}