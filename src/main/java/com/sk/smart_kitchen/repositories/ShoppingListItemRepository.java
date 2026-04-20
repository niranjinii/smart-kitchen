package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.ShoppingListItem;
import com.sk.smart_kitchen.entities.Ingredient;
import com.sk.smart_kitchen.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, Long> {
    List<ShoppingListItem> findByUserOrderByIdDesc(User user);
    
    // FIXED: Searches by Ingredient object instead of string
    Optional<ShoppingListItem> findByUserAndIngredientAndIsCheckedFalse(User user, Ingredient ingredient);
}