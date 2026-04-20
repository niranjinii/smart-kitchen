package com.sk.smart_kitchen.repositories;

import com.sk.smart_kitchen.entities.MealPlan;
import com.sk.smart_kitchen.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository 
public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    List<MealPlan> findByUserOrderByPlannedDateAsc(User user);
}