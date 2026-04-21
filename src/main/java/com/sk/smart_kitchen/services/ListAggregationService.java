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
    private final UnitConversionService unitService; // Added service

    // Updated constructor to include UnitConversionService
    public ListAggregationService(ShoppingListItemRepository repository, UnitConversionService unitService) {
        this.repository = repository;
        this.unitService = unitService;
    }

    @Transactional
    public void addOrUpdateIngredient(User user, Ingredient ingredient, double quantity, String unit) {
        
        Optional<ShoppingListItem> existingItem = repository
                .findByUserAndIngredientAndIsCheckedFalse(user, ingredient);

        if (existingItem.isPresent()) {
            ShoppingListItem item = existingItem.get();
            
            // If existing unit is empty, use the new unit
            String targetUnit = (item.getUnit() == null || item.getUnit().isEmpty()) ? unit : item.getUnit();
            
            // Logic for unit conversion and aggregation
            // 1. Convert existing quantity in DB to base metric
            double existingInBase = unitService.convertToBase(item.getQuantityNeeded(), item.getUnit());
            
            // 2. Convert incoming quantity to base metric
            double newInBase = unitService.convertToBase(quantity, unit);
            
            // 3. Sum them up in the base metric
            double totalInBase = existingInBase + newInBase;
            
            // 4. Convert the total back to the target unit
            double finalQty = unitService.convertFromBase(totalInBase, targetUnit);
            
            item.setQuantityNeeded(finalQty);
            item.setUnit(targetUnit);
            repository.save(item);
        } else {
            // New item logic remains the same
            ShoppingListItem newItem = new ShoppingListItem();
            newItem.setUser(user);
            newItem.setIngredient(ingredient); 
            newItem.setQuantityNeeded(quantity);
            newItem.setUnit(unit != null ? unit : "");
            newItem.setIsChecked(false);
            repository.save(newItem);
        }
    }
}