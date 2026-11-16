package com.example.familytree.config;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.UserRepo;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BootstrapAdmin implements ApplicationRunner {
 private final AppProperties p; private final UserRepo users; private final PasswordEncoder encoder;
 public BootstrapAdmin(AppProperties p,UserRepo users,PasswordEncoder encoder){this.p=p;this.users=users;this.encoder=encoder;}
 // Look up ALL rows to avoid restoring legacy soft-deleted accounts. Account deletion
 // rejects this configured username, so anonymization cannot cause bootstrap recreation.
 public void run(ApplicationArguments args){var a=p.bootstrapAdmin(); if(a!=null&&StringUtils.hasText(a.username())&&StringUtils.hasText(a.password())) users.findByUsername(a.username()).orElseGet(()->users.save(new User(a.username(),encoder.encode(a.password()),Role.ADMIN)));}
}
