package com.sk.smart_kitchen.services;

import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.IngredientRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ShoppingListFacade {

    private final IngredientRepository ingredientRepository;
    private final ListAggregationService aggregationService;

    public ShoppingListFacade(IngredientRepository ingredientRepository, ListAggregationService aggregationService) {
        this.ingredientRepository = ingredientRepository;
        this.aggregationService = aggregationService;
    }

    /**
     * Facade method that hides the complexity of fetching ingredients,
     * validating quantities/units, and calling the aggregation logic.
     */
    public void processAndAddIngredients(User user, List<Long> ingredientIds, List<Double> quantities, List<String> units) {
        for (int i = 0; i < ingredientIds.size(); i++) {
            Double qty = (quantities != null && quantities.size() > i) ? quantities.get(i) : 1.0;
            String unit = (units != null && units.size() > i) ? units.get(i) : "";
            
            Ingredient ingredient = ingredientRepository.findById(ingredientIds.get(i)).orElse(null);

            if (ingredient != null) {
                aggregationService.addOrUpdateIngredient(user, ingredient, qty, unit);
            }
        }
    }
}