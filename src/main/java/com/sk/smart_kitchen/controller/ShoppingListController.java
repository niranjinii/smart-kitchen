package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.ShoppingListItem;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import com.sk.smart_kitchen.repositories.PantryItemRepository;
import com.sk.smart_kitchen.repositories.ShoppingListItemRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.services.ListAggregationService;
import com.sk.smart_kitchen.services.ShoppingListFacade;
import com.sk.smart_kitchen.services.UnitConversionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/shopping-list")
public class ShoppingListController {

    private final ShoppingListItemRepository repository;
    private final UserRepository userRepository;
    private final ShoppingListFacade shoppingListFacade; // Injected Facade
    private final ListAggregationService listAggregationService;
    private final IngredientRepository ingredientRepository;
    private final UnitConversionService unitConversionService;
    private final PantryItemRepository pantryRepository;

    public ShoppingListController(ShoppingListItemRepository repository, 
                                  UserRepository userRepository, 
                                  ShoppingListFacade shoppingListFacade,
                                  ListAggregationService listAggregationService,
                                  IngredientRepository ingredientRepository,
                                  UnitConversionService unitConversionService,
                                  PantryItemRepository pantryRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.shoppingListFacade = shoppingListFacade;
        this.listAggregationService = listAggregationService;
        this.ingredientRepository = ingredientRepository;
        this.unitConversionService = unitConversionService;
        this.pantryRepository = pantryRepository;
    }

    @GetMapping
    public String viewList(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<ShoppingListItem> items = repository.findByUserOrderByIdDesc(user);
        model.addAttribute("items", items);
        return "shopping-list";
    }

    @PostMapping("/{id}/toggle")
    @ResponseBody
    public String toggleItem(@PathVariable Long id) {
        ShoppingListItem item = repository.findById(id).orElseThrow();
        item.setIsChecked(!item.getIsChecked());
        repository.save(item);
        return "ok";
    }

    @PostMapping("/{id}/delete")
    public String deleteItem(@PathVariable Long id) {
        repository.deleteById(id);
        return "redirect:/shopping-list";
    }

    @PostMapping("/add")
    public String addToList(
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds,
            @RequestParam(value = "quantities", required = false) List<Double> quantities,
            @RequestParam(value = "units", required = false) List<String> units,
            Principal principal,
            @RequestParam("recipeId") Long recipeId) {

        if (principal == null) return "redirect:/login";

        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return "redirect:/recipes/" + recipeId + "?error=no_items_selected";
        }

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        // FACADE IN ACTION: One single, clean call replaces the messy loop
        shoppingListFacade.processAndAddIngredients(user, ingredientIds, quantities, units);

        return "redirect:/recipes/" + recipeId + "?listAdded=true";
    }

    @PostMapping("/add-custom")
    public String addCustomItem(
            @RequestParam String name,
            @RequestParam Double quantity,
            @RequestParam String unit,
            Principal principal) {
        if (principal == null) return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) return "redirect:/shopping-list";

        Ingredient ingredient = ingredientRepository.findByNameIgnoreCase(cleanName)
                .orElseGet(() -> {
                    Ingredient newIng = new Ingredient();
                    newIng.setName(cleanName);
                    return ingredientRepository.save(newIng);
                });

        listAggregationService.addOrUpdateIngredient(user, ingredient, quantity, unit);
        return "redirect:/shopping-list";
    }

    @PostMapping("/transfer-to-pantry")
    public String transferToPantry(Principal principal) {
        if (principal == null) return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<ShoppingListItem> items = repository.findByUserOrderByIdDesc(user);

        for (ShoppingListItem item : items) {
            if (!item.getIsChecked()) {
                continue;
            }

            PantryItem pantryItem = pantryRepository.findByUserAndIngredient(user, item.getIngredient())
                    .orElseGet(() -> {
                        PantryItem newItem = new PantryItem();
                        newItem.setUser(user);
                        newItem.setIngredient(item.getIngredient());
                        newItem.setQuantity(0.0);
                        newItem.setCategory("Staples");
                        return newItem;
                    });

            String incomingUnit = item.getUnit() != null ? item.getUnit() : "unit";
            String canonicalIncoming = unitConversionService.canonicalizeUnit(incomingUnit);
            double incomingBase = unitConversionService.convertToBase(item.getQuantityNeeded(), incomingUnit);

            String canonicalExisting = pantryItem.getUnit() != null
                    ? unitConversionService.canonicalizeUnit(pantryItem.getUnit())
                    : canonicalIncoming;

            double existingBase = unitConversionService.convertToBase(
                    pantryItem.getQuantity() != null ? pantryItem.getQuantity() : 0.0,
                    canonicalExisting);

            double finalBase = existingBase + incomingBase;
            pantryItem.setQuantity(unitConversionService.convertFromBase(finalBase, canonicalExisting));
            pantryItem.setUnit(canonicalExisting);

            pantryRepository.save(pantryItem);
            repository.delete(item);
        }

        return "redirect:/shopping-list";
    }

    @PostMapping("/clear-checked")
    public String clearCheckedItems(Principal principal) {
        if (principal == null) return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        List<ShoppingListItem> items = repository.findByUserOrderByIdDesc(user);

        for (ShoppingListItem item : items) {
            if (item.getIsChecked()) {
                repository.delete(item);
            }
        }

        return "redirect:/shopping-list";
    }
}