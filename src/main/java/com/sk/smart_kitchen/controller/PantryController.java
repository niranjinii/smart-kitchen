package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import com.sk.smart_kitchen.repositories.PantryItemRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.services.UnitConversionService;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.security.Principal;
import java.util.List;
import java.time.LocalDate;

@Controller
@RequestMapping("/pantry")
public class PantryController {

    @org.springframework.beans.factory.annotation.Autowired 
    private com.sk.smart_kitchen.repositories.SubstitutionRepository subRepository;
    
    @org.springframework.beans.factory.annotation.Autowired 
    private com.sk.smart_kitchen.services.IngredientNormalizer normalizer;

    private final PantryItemRepository pantryRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository; // Add this!
    private final UnitConversionService unitConversionService;
    

    // Update the constructor to inject the IngredientRepository
    public PantryController(PantryItemRepository pantryRepository, UserRepository userRepository,
            IngredientRepository ingredientRepository,
            UnitConversionService unitConversionService) {
        this.pantryRepository = pantryRepository;
        this.userRepository = userRepository;
        this.ingredientRepository = ingredientRepository;
        this.unitConversionService = unitConversionService;
    }

    @GetMapping
    public String viewPantry(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<PantryItem> pantryItems = pantryRepository.findByUserOrderByIdDesc(user);

        model.addAttribute("pantryItems", pantryItems);

        // Pass today's date for the expiration check
        model.addAttribute("today", java.time.LocalDate.now());
        
        // Pass the user's permanent dietary rules
        model.addAttribute("dietaryRules", subRepository.findByUserAndRecipeIsNull(user));

        return "pantry";
    }

    // Ajax endpoint for the massive + and - buttons on mobile and spreadsheet
    // inputs
    @PostMapping("/update/{id}")
    @ResponseBody
    public String updateQuantity(@PathVariable Long id, @RequestParam Double newQuantity) {
        PantryItem item = pantryRepository.findById(id).orElseThrow();
        if (newQuantity <= 0) {
            pantryRepository.delete(item);
            return "deleted";
        } else {
            item.setQuantity(newQuantity);
            pantryRepository.save(item);
            return "updated";
        }
    }

    @PostMapping("/add")
    public String addToPantry(
            @RequestParam String name, 
            @RequestParam Double quantity, 
            @RequestParam String unit, 
            @RequestParam String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expirationDate,
            Principal principal) {
        
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        // 1. Find or create the master ingredient (Name ONLY now)
        Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Ingredient newIng = new Ingredient();
                    newIng.setName(name);
                    return ingredientRepository.save(newIng);
                });

        // 2. Find or create the user's pantry item
        PantryItem pantryItem = pantryRepository.findByUserAndIngredient(user, ingredient)
                .orElseGet(() -> {
                    PantryItem newItem = new PantryItem();
                    newItem.setUser(user);
                    newItem.setIngredient(ingredient);
                    newItem.setQuantity(0.0);
                    return newItem;
                });

        // 3. Aggregate by converting to base, then persist using canonical unit set.
        String incomingUnit = (unit == null || unit.isBlank()) ? "unit" : unit;
        String canonicalIncomingUnit = unitConversionService.canonicalizeUnit(incomingUnit);
        double incomingBase = unitConversionService.convertToBase(quantity, incomingUnit);

        String existingUnit = pantryItem.getUnit();
        String canonicalExistingUnit = (existingUnit == null || existingUnit.isBlank())
            ? canonicalIncomingUnit
            : unitConversionService.canonicalizeUnit(existingUnit);

        double existingBase = unitConversionService.convertToBase(
            pantryItem.getQuantity() != null ? pantryItem.getQuantity() : 0.0,
            canonicalExistingUnit);

        double finalBase = existingBase + incomingBase;
        double finalQuantity = unitConversionService.convertFromBase(finalBase, canonicalExistingUnit);

        pantryItem.setQuantity(finalQuantity);
        pantryItem.setUnit(canonicalExistingUnit);
        pantryItem.setCategory(category); // 🌟 SAVED TO THE USER'S PANTRY NOW
        
        if (expirationDate != null) {
            pantryItem.setExpirationDate(expirationDate);
        }
        
        pantryRepository.save(pantryItem);

        return "redirect:/pantry";
    }

    @PostMapping("/rules/add")
    public String addDietaryRule(java.security.Principal principal, 
                                 @org.springframework.web.bind.annotation.RequestParam("originalName") String originalName, 
                                 @org.springframework.web.bind.annotation.RequestParam("substituteName") String substituteName) {
        if (principal == null) return "redirect:/login";
        com.sk.smart_kitchen.entities.User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        String cleanOriginal = normalizer.normalize(originalName);
        String cleanSubstitute = normalizer.normalize(substituteName);

        if (cleanOriginal == null || cleanSubstitute == null) return "redirect:/pantry";

        com.sk.smart_kitchen.entities.Ingredient original = ingredientRepository.findByNameIgnoreCase(cleanOriginal)
                .orElseGet(() -> {
                    com.sk.smart_kitchen.entities.Ingredient newIng = new com.sk.smart_kitchen.entities.Ingredient();
                    newIng.setName(cleanOriginal);
                    return ingredientRepository.save(newIng);
                });

        com.sk.smart_kitchen.entities.Ingredient substitute = ingredientRepository.findByNameIgnoreCase(cleanSubstitute)
                .orElseGet(() -> {
                    com.sk.smart_kitchen.entities.Ingredient newIng = new com.sk.smart_kitchen.entities.Ingredient();
                    newIng.setName(cleanSubstitute);
                    return ingredientRepository.save(newIng);
                });

        com.sk.smart_kitchen.entities.Substitution rule = subRepository.findByOriginalIngredientAndUser(original, user)
                .stream().filter(s -> s.getRecipe() == null).findFirst().orElse(new com.sk.smart_kitchen.entities.Substitution());

        rule.setOriginalIngredient(original);
        rule.setSubstituteIngredient(substitute);
        rule.setUser(user);
        rule.setRecipe(null); // Explicitly null for GLOBAL scope
        rule.setConversionMultiplier(1.0);
        rule.setNotes("Permanent Dietary Preference");
        
        subRepository.save(rule);
        return "redirect:/pantry";
    }

    @PostMapping("/rules/remove")
    public String removeDietaryRule(java.security.Principal principal, @org.springframework.web.bind.annotation.RequestParam("ruleId") Long ruleId) {
        if (principal == null) return "redirect:/login";
        com.sk.smart_kitchen.entities.User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        
        subRepository.findById(ruleId).ifPresent(rule -> {
            if (rule.getUser() != null && rule.getUser().getId().equals(user.getId())) {
                subRepository.delete(rule);
            }
        });
        return "redirect:/pantry";
    }
}