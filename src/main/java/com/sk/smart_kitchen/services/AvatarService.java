package com.sk.smart_kitchen.services;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
public class AvatarService {

    public String buildDefaultAvatarUrl(String displayName) {
        String safeName = displayName == null || displayName.trim().isEmpty()
                ? "User"
                : displayName.trim();

        return "https://ui-avatars.com/api/?name="
                + UriUtils.encode(safeName, StandardCharsets.UTF_8)
                + "&background=0D8ABC&color=fff&bold=true";
    }

    public boolean isDefaultAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return true;
        }
        return avatarUrl.contains("ui-avatars.com/api/");
    }
}
