package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.MealPlan;
import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.MealPlanRepository;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/planner")
public class PlannerController {

    private final MealPlanRepository mealPlanRepository;
    private final UserRepository userRepository;
    private final RecipeRepository recipeRepository;

    public PlannerController(MealPlanRepository mealPlanRepository, UserRepository userRepository, RecipeRepository recipeRepository) {
        this.mealPlanRepository = mealPlanRepository;
        this.userRepository = userRepository;
        this.recipeRepository = recipeRepository;
    }

    @GetMapping
    public String viewPlanner(Model model, Principal principal, 
                          @RequestParam(defaultValue = "0") int offsetWeeks) {
    if (principal == null) return "redirect:/login";

    // 1. Month Limit Guard (Point #2)
    if (offsetWeeks < 0) return "redirect:/planner?offsetWeeks=0";
    if (offsetWeeks > 4) return "redirect:/planner?offsetWeeks=4";

    User user = userRepository.findByEmail(principal.getName()).orElseThrow();

    // 2. Rolling Logic: Start from "Today" + offset instead of "Previous Monday"
    LocalDate startDate = LocalDate.now().plusWeeks(offsetWeeks);

    List<LocalDate> currentWeek = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
        currentWeek.add(startDate.plusDays(i));
    }

    List<MealPlan> allPlans = mealPlanRepository.findByUserOrderByPlannedDateAsc(user);
    Map<LocalDate, List<MealPlan>> plansByDate = allPlans.stream()
            .collect(Collectors.groupingBy(MealPlan::getPlannedDate));

    model.addAttribute("weekDays", currentWeek);
    model.addAttribute("plansByDate", plansByDate);
    model.addAttribute("today", LocalDate.now());
    model.addAttribute("offsetWeeks", offsetWeeks); // For the UI buttons

    return "planner";
}

    @PostMapping("/add")
    public String addToPlanner(@RequestParam Long recipeId, 
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedDate, 
                               @RequestParam String mealType, 
                               Principal principal) {
        if (principal == null) return "redirect:/login";

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        Recipe recipe = recipeRepository.findById(recipeId).orElseThrow();

        MealPlan plan = new MealPlan();
        plan.setUser(user);
        plan.setRecipe(recipe);
        plan.setPlannedDate(plannedDate);
        plan.setMealType(mealType);
        
        mealPlanRepository.save(plan);

        return "redirect:/planner";
    }

    // NEW: Delete from Planner
    @PostMapping("/{id}/delete")
    public String deleteFromPlanner(@PathVariable Long id, Principal principal) {
        if (principal != null) {
            mealPlanRepository.deleteById(id);
        }
        return "redirect:/planner";
    }

    // NEW: Move Meal to Different Date (Drag & Drop)
    @PostMapping("/{id}/move")
    @ResponseBody
    public String moveMeal(@PathVariable Long id, 
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newDate) {
        MealPlan plan = mealPlanRepository.findById(id).orElseThrow();
        plan.setPlannedDate(newDate);
        mealPlanRepository.save(plan);
        return "ok";
    }
}