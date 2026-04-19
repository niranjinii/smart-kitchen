package com.sk.smart_kitchen.config;

import com.sk.smart_kitchen.entities.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {
    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // This is the magic method we will call from the HTML!
    public String getDisplayName() {
        return user.getUsername(); 
    }

    public String getProfileImageUrl() {
        return user.getProfileImageUrl();
    }

    @Override
    public String getUsername() {
        return user.getEmail(); // Keep using email for the actual login math
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
