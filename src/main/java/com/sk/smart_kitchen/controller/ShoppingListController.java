package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.ShoppingListItem;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.repositories.ShoppingListItemRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import com.sk.smart_kitchen.services.ListAggregationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/shopping-list")
public class ShoppingListController {

    private final ShoppingListItemRepository repository;
    private final ListAggregationService aggregationService;
    private final UserRepository userRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    public ShoppingListController(ShoppingListItemRepository repository, ListAggregationService aggregationService, UserRepository userRepository) {
        this.repository = repository;
        this.aggregationService = aggregationService;
        this.userRepository = userRepository;
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

        for (int i = 0; i < ingredientIds.size(); i++) {
            Double qty = (quantities != null && quantities.size() > i) ? quantities.get(i) : 1.0;
            String unit = (units != null && units.size() > i) ? units.get(i) : "";
            Ingredient ingredient = ingredientRepository.findById(ingredientIds.get(i)).orElse(null);

            if (ingredient != null) {
                // FIXED: We now pass the entire Ingredient object to the service!
                aggregationService.addOrUpdateIngredient(user, ingredient, qty, unit);
                // FIXED: We now pass the entire Ingredient object to the service!
                aggregationService.addOrUpdateIngredient(user, ingredient, qty, unit);
            }
        }

        return "redirect:/recipes/" + recipeId + "?listAdded=true";
    }
}