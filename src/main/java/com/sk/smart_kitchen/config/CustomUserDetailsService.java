package com.sk.smart_kitchen.config;

import com.sk.smart_kitchen.entities.User;
import com.sk.smart_kitchen.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList; // Used for Roles/Authorities

@Service
public class CustomUserDetailsService implements UserDetailsService {

    // DIP in action: Depending on an abstraction (Interface), not a concrete class!
    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // THIS IS THE CRUCIAL LINE: 
        // It must return our custom class, not the default Spring User!
        return new CustomUserDetails(user);
    }
}