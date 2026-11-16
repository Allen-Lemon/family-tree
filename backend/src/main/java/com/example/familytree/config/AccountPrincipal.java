package com.example.familytree.config;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/** Bind authenticated sessions to an immutable database id, never a reusable username. */
public final class AccountPrincipal extends User {
    private static final long serialVersionUID = 1L;
    private final long userId;

    public AccountPrincipal(long userId, UserDetails user) {
        super(user.getUsername(), user.getPassword(), user.isEnabled(), user.isAccountNonExpired(),
                user.isCredentialsNonExpired(), user.isAccountNonLocked(), user.getAuthorities());
        this.userId = userId;
    }

    public long getUserId() { return userId; }
}
