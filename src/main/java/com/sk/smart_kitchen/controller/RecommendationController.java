package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.services.RecommendationEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    @Autowired
    private RecommendationEngine recommendationEngine;
    
    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<RecommendationEngine.RecipeMatchDTO>> getRecommendations(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        
        return ResponseEntity.ok(recommendationEngine.getTopRecommendations(user));
    }
}
