package com.example.familytree.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Revalidate identity and authorities before CSRF, logout and authorization on every request. */
final class DatabaseSessionFilter extends OncePerRequestFilter {
 private final JdbcClient jdbc;
 DatabaseSessionFilter(JdbcClient jdbc){this.jdbc=jdbc;}
 private record Account(String username,String password,String role,boolean enabled,boolean deleted){}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
  var previous=SecurityContextHolder.getContext().getAuthentication();
  if(previous!=null && !(previous instanceof AnonymousAuthenticationToken)){
   // Legacy sessions without a stable id must log in again; resolving their old username
   // would resurrect them as a different account after username reuse.
   var identity=previous.getPrincipal() instanceof AccountPrincipal p?p:null;
   var account=identity==null?java.util.Optional.<Account>empty():jdbc.sql("select username,password_hash,role,enabled,deleted from users where id=:id")
    .param("id",identity.getUserId()).query((rs,n)->new Account(rs.getString("username"),rs.getString("password_hash"),rs.getString("role"),rs.getBoolean("enabled"),rs.getBoolean("deleted"))).optional();
   if(account.isEmpty()||!account.get().enabled()||account.get().deleted()){
    SecurityContextHolder.clearContext();
    var session=request.getSession(false);if(session!=null)session.invalidate();
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"账号不可用，请重新登录\"}");
    return;
   }
   var row=account.get();
   var principal=new AccountPrincipal(identity.getUserId(),User.withUsername(row.username()).password(row.password()).roles(row.role()).build());
   var context=SecurityContextHolder.createEmptyContext();
   context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
   SecurityContextHolder.setContext(context);
   var session=request.getSession(false);
   if(session!=null)session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,context);
  }
  chain.doFilter(request,response);
 }
}
