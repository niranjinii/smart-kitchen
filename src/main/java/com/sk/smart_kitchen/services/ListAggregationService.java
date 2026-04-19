package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.ShoppingListItem;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.ShoppingListItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ListAggregationService {

    private final ShoppingListItemRepository repository;

    public ListAggregationService(ShoppingListItemRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void addOrUpdateIngredient(User user, Ingredient ingredient, double quantity, String unit) {
        
        Optional<ShoppingListItem> existingItem = repository
                .findByUserAndIngredientAndIsCheckedFalse(user, ingredient);

        if (existingItem.isPresent()) {
            ShoppingListItem item = existingItem.get();
            item.setQuantityNeeded(item.getQuantityNeeded() + quantity);
            repository.save(item);
        } else {
            ShoppingListItem newItem = new ShoppingListItem();
            newItem.setUser(user);
            // FIXED: Saves the object so PostgreSQL gets the ingredient_id
            newItem.setIngredient(ingredient); 
            newItem.setQuantityNeeded(quantity);
            newItem.setUnit(unit != null ? unit : "");
            newItem.setIsChecked(false);
            repository.save(newItem);
        }
    }
}