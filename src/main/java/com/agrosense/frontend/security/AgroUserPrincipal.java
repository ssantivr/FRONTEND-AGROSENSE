package com.agrosense.frontend.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** Authenticated user, exposing the display data the top bar needs. */
@Getter
public class AgroUserPrincipal extends User {

    private final String displayName;
    private final String initials;

    public AgroUserPrincipal(String email, String passwordHash, boolean enabled,
            Collection<? extends GrantedAuthority> authorities, String name, String lastName) {
        super(email, passwordHash, enabled, true, true, true, authorities);
        this.displayName = (name + " " + lastName).trim();
        this.initials = (firstLetter(name) + firstLetter(lastName)).toUpperCase();
    }

    private static String firstLetter(String value) {
        return value == null || value.isBlank() ? "" : value.trim().substring(0, 1);
    }
}
