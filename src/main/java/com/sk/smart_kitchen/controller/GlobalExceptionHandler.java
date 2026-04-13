package com.sk.smart_kitchen.controller;

import jakarta.servlet.http.HttpServletRequest;
import com.sk.smart_kitchen.services.RecipeService;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final RecipeService recipeService;

    public GlobalExceptionHandler(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            Model model
    ) {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/recipes")) {
            String message = ex.getMostSpecificCause() != null
                    ? ex.getMostSpecificCause().getMessage()
                    : ex.getMessage();
            model.addAttribute("uploadError", "Upload failed. Please use an image under 25MB. Details: " + message);
            model.addAttribute("recipeForm", recipeService.emptyForm());
            model.addAttribute("isEdit", false);
            return "publish";
        }
        return "redirect:/";
    }
}
