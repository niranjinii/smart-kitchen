package com.sk.smart_kitchen.services;

import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class IngredientNormalizer {

    private static final Set<String> FLUFF_WORDS = Set.of(
            "large", "medium", "small", "extra", "fresh", "organic", "raw", "whole", "jumbo",
            "skimmed", "skim", "low-fat", "full-fat", "non-fat", "unsalted", "salted",
            "shredded", "grated", "diced", "chopped", "minced", "sliced", "peeled", "crushed", "melted"
    );

    private static final Set<String> UNTOUCHABLES = Set.of(
            "asparagus", "molasses", "lemongrass", "couscous", "hummus", "watercress"
    );

    // 🌟 NEW: Words that are naturally plural in cooking context and shouldn't be touched!
    private static final Set<String> PLURAL_EXCEPTIONS = Set.of(
            "beans", "peas", "lentils", "oats", "greens", "berries", "cloves", "leeks", "tortillas", "chips", "flakes", "noodles", "chives", "shallots"
    );

    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;

        String cleaned = rawName.toLowerCase()
                .replaceAll("to taste", "")
                .replaceAll("just a splash", "")
                .replaceAll("a splash", "")
                .replaceAll("[^a-z\\s]", "")
                .trim();

        cleaned = Arrays.stream(cleaned.split("\\s+"))
                .filter(word -> !FLUFF_WORDS.contains(word))
                .collect(Collectors.joining(" "));

        if (cleaned.isBlank()) {
             return capitalizeWords(rawName.replaceAll("[^a-zA-Z\\s]", "").trim());
        }

        if (UNTOUCHABLES.contains(cleaned)) return capitalizeWords(cleaned);

        // Check if the ingredient ends with any of our safe plural words
        boolean isPluralException = false;
        for (String exception : PLURAL_EXCEPTIONS) {
            if (cleaned.endsWith(exception)) {
                isPluralException = true;
                break;
            }
        }

        // Only run the de-pluralizer if it's NOT an exception!
        if (!isPluralException) {
            if (cleaned.endsWith("oes") && cleaned.length() > 4) { 
                cleaned = cleaned.substring(0, cleaned.length() - 2);
            } else if (cleaned.endsWith("ies") && cleaned.length() > 4) { 
                cleaned = cleaned.substring(0, cleaned.length() - 3) + "y";
            } else if (cleaned.endsWith("s") && !cleaned.endsWith("ss") && cleaned.length() > 3) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
        }

        return capitalizeWords(cleaned);
    }

    private String capitalizeWords(String text) {
        return Arrays.stream(text.split("\\s+"))
                .map(word -> word.isEmpty() ? "" : word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}