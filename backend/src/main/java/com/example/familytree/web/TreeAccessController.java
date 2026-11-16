package com.example.familytree.web;

import com.example.familytree.domain.DomainModels.AccessRole;
import com.example.familytree.repo.Repositories.UserRepo;
import com.example.familytree.service.AccessService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/trees/{tree}/access")
public class TreeAccessController {
 private final AccessService access; private final UserRepo users; private final JdbcClient jdbc; private final com.example.familytree.service.UserAdministrationService administration;
 public TreeAccessController(AccessService a,UserRepo u,JdbcClient j,com.example.familytree.service.UserAdministrationService administration){access=a;users=u;jdbc=j;this.administration=administration;}
 record Grant(String username,AccessRole role){}
 @GetMapping List<Map<String,Object>> list(@PathVariable Long tree,Authentication a){access.requireOwner(tree,a);return jdbc.sql("select u.id,u.username,a.access_role from tree_access a join users u on u.id=a.user_id where a.tree_id=:t and u.deleted=false").param("t",tree).query().listOfRows();}
 @org.springframework.transaction.annotation.Transactional
 @PutMapping void grant(@PathVariable Long tree,@RequestBody Grant g,Authentication a){access.requireOwner(tree,a);var u=users.findByUsernameAndDeletedFalse(g.username).orElseThrow(()->new AccessService.NotFoundException("用户不存在"));var role=g.role==null?AccessRole.VIEWER:g.role;jdbc.sql("insert into tree_access(tree_id,user_id,access_role) values(:t,:u,:r) on duplicate key update access_role=:r").param("t",tree).param("u",u.getId()).param("r",role.name()).update();}
 @DeleteMapping("/{userId}") void revoke(@PathVariable Long tree,@PathVariable Long userId,Authentication a){administration.revokeTreeAccess(tree,userId,a);}
}
