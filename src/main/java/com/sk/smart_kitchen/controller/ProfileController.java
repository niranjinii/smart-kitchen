package com.sk.smart_kitchen.controller;

import com.sk.smart_kitchen.entities.Recipe;
import com.sk.smart_kitchen.entities.SavedRecipe;
import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.RecipeRepository;
import com.sk.smart_kitchen.repositories.SavedRecipeRepository;
import com.sk.smart_kitchen.repositories.UserRepository;
import com.sk.smart_kitchen.services.AvatarService;
import com.sk.smart_kitchen.services.ImageStorageService;
import com.sk.smart_kitchen.services.RecipeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final SavedRecipeRepository savedRecipeRepository;
    private final RecipeRepository recipeRepository;
    private final RecipeService recipeService;
    private final ImageStorageService imageStorageService;
    private final PasswordEncoder passwordEncoder;
    private final AvatarService avatarService;

    public ProfileController(
            UserRepository userRepository,
            SavedRecipeRepository savedRecipeRepository,
            RecipeRepository recipeRepository,
            RecipeService recipeService,
            ImageStorageService imageStorageService,
            PasswordEncoder passwordEncoder,
            AvatarService avatarService) {
        this.userRepository = userRepository;
        this.savedRecipeRepository = savedRecipeRepository;
        this.recipeRepository = recipeRepository;
        this.recipeService = recipeService;
        this.imageStorageService = imageStorageService;
        this.passwordEncoder = passwordEncoder;
        this.avatarService = avatarService;
    }

    @GetMapping
    public String showProfile(
            @RequestParam(value = "tab", required = false, defaultValue = "bookmarks") String tab,
            @RequestParam(value = "updated", required = false) Boolean updated,
            @RequestParam(value = "deleted", required = false) Boolean deleted,
            @RequestParam(value = "error", required = false) String error,
            Principal principal,
            Model model) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        String activeTab = normalizeTab(tab);

        populateProfileModel(model, user, activeTab);
        model.addAttribute("updated", Boolean.TRUE.equals(updated));
        model.addAttribute("deleted", Boolean.TRUE.equals(deleted));
        model.addAttribute("profileError", error);

        return "profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @RequestParam("displayName") String displayName,
            @RequestParam("email") String email,
            @RequestParam(value = "newPassword", required = false) String newPassword,
            @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
            @RequestParam(value = "profileImageFile", required = false) MultipartFile profileImageFile,
            Principal principal,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {

        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();

        String cleanedDisplayName = trimToNull(displayName);
        String cleanedEmail = trimToNull(email);
        String cleanedNewPassword = trimToNull(newPassword);
        String cleanedConfirmPassword = trimToNull(confirmPassword);

        if (cleanedDisplayName == null) {
            return renderValidationError(model, user, "Display name is required.");
        }
        if (cleanedEmail == null) {
            return renderValidationError(model, user, "Email is required.");
        }

        boolean displayNameTaken = userRepository.findByUsername(cleanedDisplayName)
                .filter(found -> !found.getId().equals(user.getId()))
                .isPresent();
        if (displayNameTaken) {
            return renderValidationError(model, user, "Display name is already taken.");
        }

        boolean emailTaken = userRepository.findByEmail(cleanedEmail)
                .filter(found -> !found.getId().equals(user.getId()))
                .isPresent();
        if (emailTaken) {
            return renderValidationError(model, user, "Email is already in use.");
        }

        if (cleanedNewPassword != null && cleanedNewPassword.length() < 8) {
            return renderValidationError(model, user, "Password must be at least 8 characters.");
        }

        if (cleanedNewPassword != null && !Objects.equals(cleanedNewPassword, cleanedConfirmPassword)) {
            return renderValidationError(model, user, "New password and confirm password do not match.");
        }

        boolean displayNameChanged = !cleanedDisplayName.equals(user.getUsername());
        boolean emailChanged = !cleanedEmail.equalsIgnoreCase(user.getEmail());
        boolean passwordChanged = cleanedNewPassword != null;
        boolean usingDefaultAvatar = avatarService.isDefaultAvatarUrl(user.getProfileImageUrl());
        String uploadedProfileUrl = null;

        if (profileImageFile != null && !profileImageFile.isEmpty()) {
            try {
                uploadedProfileUrl = imageStorageService.storeProfileImage(profileImageFile);
            } catch (IllegalArgumentException ex) {
                return renderValidationError(model, user, ex.getMessage());
            }
        }

        user.setUsername(cleanedDisplayName);
        user.setEmail(cleanedEmail);

        if (uploadedProfileUrl != null) {
            user.setProfileImageUrl(uploadedProfileUrl);
        } else if (displayNameChanged && usingDefaultAvatar) {
            user.setProfileImageUrl(avatarService.buildDefaultAvatarUrl(cleanedDisplayName));
        } else if (trimToNull(user.getProfileImageUrl()) == null) {
            user.setProfileImageUrl(avatarService.buildDefaultAvatarUrl(cleanedDisplayName));
        }

        if (passwordChanged) {
            user.setPassword(passwordEncoder.encode(cleanedNewPassword));
        }

        userRepository.save(user);

        if (emailChanged || passwordChanged) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return "redirect:/login?profileUpdated=true";
        }

        return "redirect:/profile?updated=true";
    }

    @PostMapping("/bookmarks/{recipeId}/remove")
    public String removeBookmark(@PathVariable Long recipeId, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByEmail(principal.getName()).orElseThrow();
        recipeRepository.findById(recipeId).ifPresent(recipe ->
                savedRecipeRepository.findByUserAndRecipe(user, recipe)
                        .ifPresent(savedRecipeRepository::delete));

        return "redirect:/profile?tab=bookmarks";
    }

    @PostMapping("/recipes/{recipeId}/delete")
    public String deleteOwnRecipe(@PathVariable Long recipeId) {
        try {
            recipeService.deleteRecipe(recipeId);
            return "redirect:/profile?tab=recipes&deleted=true";
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipe not found", ex);
        } catch (SecurityException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage(), ex);
        }
    }

    private void populateProfileModel(Model model, User user, String activeTab) {
        List<Recipe> bookmarkedRecipes = savedRecipeRepository.findByUserOrderBySavedAtDesc(user)
                .stream()
                .map(SavedRecipe::getRecipe)
                .filter(Objects::nonNull)
                .toList();

        List<Recipe> uploadedRecipes = recipeRepository.findByAuthorOrderByIdDesc(user);

        model.addAttribute("userProfile", user);
        model.addAttribute("bookmarkedRecipes", bookmarkedRecipes);
        model.addAttribute("uploadedRecipes", uploadedRecipes);
        model.addAttribute("activeTab", activeTab);
    }

    private String normalizeTab(String tab) {
        if ("recipes".equalsIgnoreCase(tab)) {
            return "recipes";
        }
        return "bookmarks";
    }

    private String renderValidationError(Model model, User user, String message) {
        populateProfileModel(model, user, "bookmarks");
        model.addAttribute("profileError", message);
        return "profile";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
