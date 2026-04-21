package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.PantryItem;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.entities.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {
    List<PantryItem> findByUserOrderByIdDesc(User user);
    Optional<PantryItem> findByUserAndIngredient(User user, Ingredient ingredient);
}