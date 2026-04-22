package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.services.AvatarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AvatarService avatarService;

    // --- LOGIN ---
    @GetMapping("/login")
    public String showLoginForm(
            @org.springframework.web.bind.annotation.RequestParam(name = "continue", required = false) String continueUrl, 
            Model model,
            java.security.Principal principal) {
        
        // If the user is ALREADY logged in, bounce them to the home page
        if (principal != null) {
            return "redirect:/";
        }

        // Pass the URL directly to the HTML page
        if (continueUrl != null) {
            model.addAttribute("redirectUrl", continueUrl);
        }
        
        return "login";
    }
    // --- REGISTRATION ---
    @GetMapping("/register")
    public String showRegistrationForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(User user, Model model) {
        // 1. Check if email already exists
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            model.addAttribute("error", "An account with that email already exists.");
            return "register";
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            model.addAttribute("error", "That Display Name is already taken. Please choose another.");
            return "register";
        }

        // 2. Encrypt the password before saving!
        String plainPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(plainPassword));

        // 3. Give them a default avatar if they didn't provide one
        if (user.getProfileImageUrl() == null || user.getProfileImageUrl().isEmpty()) {
            user.setProfileImageUrl(avatarService.buildDefaultAvatarUrl(user.getUsername()));
        }

        // 4. Save to DB
        userRepository.save(user);

        // Redirect to login with a success message
        return "redirect:/login?registered=true";
    }
}