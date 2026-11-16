package com.example.familytree.config;

import com.example.familytree.repo.Repositories.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.*;

@Aspect @Component
public class AuditAspect {
 private final JdbcClient jdbc; private final UserRepo users;
 public AuditAspect(JdbcClient j,UserRepo u){jdbc=j;users=u;}
 @Around("execution(@org.springframework.web.bind.annotation.PostMapping * *(..)) || execution(@org.springframework.web.bind.annotation.PutMapping * *(..)) || execution(@org.springframework.web.bind.annotation.DeleteMapping * *(..))")
 public Object audit(ProceedingJoinPoint p)throws Throwable{Object result=p.proceed();try{var a=SecurityContextHolder.getContext().getAuthentication();if(a!=null&&a.isAuthenticated()){var u=users.findByUsername(a.getName()).orElse(null);var attrs=(ServletRequestAttributes)RequestContextHolder.getRequestAttributes();HttpServletRequest req=attrs==null?null:attrs.getRequest();String path=req==null?"":req.getRequestURI();Long treeId=null;var match=java.util.regex.Pattern.compile("/trees/(\\d+)").matcher(path);if(match.find())treeId=Long.valueOf(match.group(1));jdbc.sql("insert into audit_logs(tree_id,user_id,action,details_json,ip_address) values(:t,:u,:a,:d,:ip)").param("t",treeId).param("u",u==null?null:u.getId()).param("a",p.getSignature().toShortString()).param("d","{\"method\":\""+(req==null?"":req.getMethod())+"\",\"path\":\""+path+"\"}").param("ip",req==null?null:req.getRemoteAddr()).update();}}catch(Exception ignored){}return result;}
}
