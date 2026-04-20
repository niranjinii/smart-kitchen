package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import com.sk.smart_kitchen.repositories.PantryItemRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
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

    private final PantryItemRepository pantryRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository; // Add this!

    // Update the constructor to inject the IngredientRepository
    public PantryController(PantryItemRepository pantryRepository, UserRepository userRepository,
            IngredientRepository ingredientRepository) {
        this.pantryRepository = pantryRepository;
        this.userRepository = userRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @GetMapping
    public String viewPantry(Model model, Principal principal) {
        if (principal == null)
            return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<PantryItem> pantryItems = pantryRepository.findByUserOrderByIdDesc(user);

        model.addAttribute("pantryItems", pantryItems);
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

        // 3. Update all the user-specific data!
        pantryItem.setQuantity(pantryItem.getQuantity() + quantity);
        pantryItem.setUnit(unit);
        pantryItem.setCategory(category); // 🌟 SAVED TO THE USER'S PANTRY NOW
        
        if (expirationDate != null) {
            pantryItem.setExpirationDate(expirationDate);
        }
        
        pantryRepository.save(pantryItem);

        return "redirect:/pantry";
    }
}