package com.example.familytree.web;

import com.example.familytree.service.AccessService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.LinkedHashMap;
import com.example.familytree.domain.DomainModels.User;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthenticationManager manager; private final AccessService access;
 public AuthController(AuthenticationManager m,AccessService a){manager=m;access=a;}
 record Login(@NotBlank String username,@NotBlank String password){}
 @PostMapping("/login") public Map<String,Object> login(@Valid @RequestBody Login x,HttpServletRequest req){var a=manager.authenticate(new UsernamePasswordAuthenticationToken(x.username(),x.password()));var u=access.current(a);req.getSession(true);req.changeSessionId();var c=SecurityContextHolder.createEmptyContext();c.setAuthentication(a);SecurityContextHolder.setContext(c);req.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,c);return identity(u);}
 @GetMapping("/me") public Map<String,Object> me(Authentication a){var u=access.current(a);return identity(u);}
 private Map<String,Object> identity(User u){var result=new LinkedHashMap<String,Object>();result.put("id",u.getId());result.put("username",u.getUsername());result.put("role",u.getRole());result.put("memberId",u.getMemberId());result.put("memberTreeId",access.memberTreeId(u));return result;}
 @GetMapping("/csrf") public Map<String,String> csrf(org.springframework.security.web.csrf.CsrfToken t){return Map.of("headerName",t.getHeaderName(),"parameterName",t.getParameterName(),"token",t.getToken());}
}
