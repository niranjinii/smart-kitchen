package com.sk.smart_kitchen.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        // This tells Spring Boot to look in the templates folder for "home.html"
        return "home"; 
    }

    @GetMapping("/feed")
    public String viewFeed() {
        return "feed"; // Tells Spring to load feed.html
    }
}

