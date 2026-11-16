package com.example.familytree.service;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AccessService {
 private final UserRepo users; private final TreeRepo trees; private final JdbcClient jdbc;
 public AccessService(UserRepo users,TreeRepo trees,JdbcClient jdbc){this.users=users;this.trees=trees;this.jdbc=jdbc;}
 public User current(Authentication a){
  if(a==null)throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException("请先登录");
  // Never authorize from session authorities or a potentially stale JPA first-level cache.
  var u=jdbc.sql("select id,username,role,enabled,deleted,member_id from users where username=:name").param("name",a.getName()).query((rs,n)->{var row=new User(rs.getString("username"),null,Role.valueOf(rs.getString("role")));row.setId(rs.getLong("id"));row.setEnabled(rs.getBoolean("enabled"));row.setDeleted(rs.getBoolean("deleted"));row.setMemberId(rs.getObject("member_id",Long.class));return row;}).optional().orElseThrow(()->new org.springframework.security.authentication.BadCredentialsException("账号不可用"));
  if(a.getPrincipal() instanceof com.example.familytree.config.AccountPrincipal p && p.getUserId()!=u.getId())throw new org.springframework.security.authentication.BadCredentialsException("账号不可用");
  if(u.isDeleted()||!u.isEnabled())throw new org.springframework.security.authentication.DisabledException("账号不可用");return u;
 }
 public User requireAdmin(Authentication a){var u=current(a);if(u.getRole()!=Role.ADMIN)throw new AccessDeniedException("仅管理员可操作");return u;}
 public FamilyTree requireRead(Long treeId,Authentication a){var u=current(a);var t=trees.findById(treeId).orElseThrow(()->new NotFoundException("家谱不存在"));if(u.getRole()==Role.ADMIN||canRead(treeId,u.getId()))return t;throw new AccessDeniedException("无此家谱访问权限");}
 public boolean canRead(Long treeId,Long userId){return jdbc.sql("select count(*) from family_trees t where t.id=:tree and (t.owner_id=:user or exists (select 1 from tree_access a where a.tree_id=t.id and a.user_id=:user))").param("tree",treeId).param("user",userId).query(Long.class).single()>0;}
 public FamilyTree requireEdit(Long treeId,Authentication a){requireAdmin(a);return requireRead(treeId,a);}
 public FamilyTree requirePersonnelEdit(Long treeId,Authentication a){var u=current(a);if(u.getRole()!=Role.ADMIN&&u.getRole()!=Role.FAMILY_ADMIN)throw new AccessDeniedException("仅管理员可管理人员资料");return requireRead(treeId,a);}
 public FamilyTree requireOwner(Long treeId,Authentication a){return requireEdit(treeId,a);}
 public void requireMemberEdit(Long treeId,Long memberId,Authentication a){
  requireRead(treeId,a);var u=current(a);
  if(u.getRole()!=Role.ADMIN&&u.getRole()!=Role.FAMILY_ADMIN&&!java.util.Objects.equals(u.getMemberId(),memberId))throw new AccessDeniedException("仅可修改绑定的本人资料");
  if(jdbc.sql("select count(*) from members where id=:id and tree_id=:tree and deleted=false").param("id",memberId).param("tree",treeId).query(Long.class).single()==0)throw new NotFoundException("成员不存在");
 }
 public void clearMemberBindings(Long memberId){for(var user:users.findAllByOrderByIdAsc())if(memberId.equals(user.getMemberId())){user.setMemberId(null);users.save(user);}users.flush();}
 public Long memberTreeId(User user){if(user.getMemberId()==null)return null;return jdbc.sql("select tree_id from members where id=:id and deleted=false").param("id",user.getMemberId()).query(Long.class).optional().orElse(null);}
 public AccessRole treeRole(FamilyTree tree,User user){if(user.getRole()==Role.ADMIN||tree.getOwnerId().equals(user.getId()))return AccessRole.OWNER;return jdbc.sql("select access_role from tree_access where tree_id=:tree and user_id=:user").param("tree",tree.getId()).param("user",user.getId()).query(String.class).optional().map(AccessRole::valueOf).orElse(null);}
 public void requireMedia(Long tree,Long media){if(media!=null&&jdbc.sql("select count(*) from media where id=:media and tree_id=:tree").param("media",media).param("tree",tree).query(Long.class).single()==0)throw new NotFoundException("媒体不存在");}
 public static class NotFoundException extends RuntimeException{public NotFoundException(String m){super(m);}}
 public static class ConflictException extends RuntimeException{public ConflictException(String m){super(m);}}
 public static class BadRequestException extends RuntimeException{public BadRequestException(String m){super(m);}}
}
