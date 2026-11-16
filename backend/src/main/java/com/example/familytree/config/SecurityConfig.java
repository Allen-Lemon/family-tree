package com.example.familytree.config;

import com.example.familytree.repo.Repositories.UserRepo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean UserDetailsService userDetailsService(UserRepo users){return username->{var u=users.findByUsername(username).orElseThrow(()->new UsernameNotFoundException(username));return new AccountPrincipal(u.getId(),User.withUsername(u.getUsername()).password(u.getPasswordHash()).roles(u.getRole().name()).disabled(u.isDeleted()||!u.isEnabled()).build());};}
 @Bean AuthenticationManager authenticationManager(AuthenticationConfiguration c)throws Exception{return c.getAuthenticationManager();}
 @Bean SecurityFilterChain security(HttpSecurity http,org.springframework.jdbc.core.simple.JdbcClient jdbc)throws Exception{
  var csrf=CookieCsrfTokenRepository.withHttpOnlyFalse(); csrf.setCookiePath("/");
  var csrfHandler=new CsrfTokenRequestAttributeHandler(); csrfHandler.setCsrfRequestAttributeName(null);
  return http.addFilterAfter(new DatabaseSessionFilter(jdbc),org.springframework.security.web.context.SecurityContextHolderFilter.class)
   .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(csrfHandler)).authorizeHttpRequests(a->a
    .requestMatchers("/api/v1/auth/login","/api/v1/auth/csrf","/v3/api-docs/**","/swagger-ui/**","/swagger-ui.html","/actuator/health").permitAll()
    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().authenticated())
   .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->r.sendError(HttpServletResponse.SC_UNAUTHORIZED)).accessDeniedHandler((q,r,x)->r.sendError(HttpServletResponse.SC_FORBIDDEN)))
   .logout(l->l.logoutUrl("/api/v1/auth/logout").logoutSuccessHandler((q,r,a)->r.setStatus(204))).build();
 }
}
