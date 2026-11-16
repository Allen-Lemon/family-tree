package com.example.familytree.service;

import com.example.familytree.config.AppProperties;
import com.example.familytree.domain.DomainModels.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.example.familytree.repo.Repositories.*;
import com.example.familytree.service.AccessService.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class UserAdministrationService {
 private final UserRepo users; private final TreeRepo trees; private final AccessService access;
 private final PasswordEncoder encoder; private final JdbcClient jdbc;
 private final ProfilePortraitStorage portraits; private final AppProperties properties;
 public UserAdministrationService(UserRepo users,TreeRepo trees,AccessService access,PasswordEncoder encoder,JdbcClient jdbc,ProfilePortraitStorage portraits,AppProperties properties){this.users=users;this.trees=trees;this.access=access;this.encoder=encoder;this.jdbc=jdbc;this.portraits=portraits;this.properties=properties;}
 public record UserDto(Long id,String username,Role role,boolean enabled,Instant createdAt,List<Long> assignedTreeIds,Long memberId,Long memberTreeId,String memberName){}
 @Transactional(readOnly=true) public List<UserDto> list(Authentication auth){access.requireAdmin(auth);return users.findByDeletedFalseOrderByIdAsc().stream().map(this::dto).toList();}
 @Transactional public UserDto create(String username,String password,Role role,List<Long> treeIds,Authentication auth){
  users.findAllByOrderByIdAsc();access.requireAdmin(auth);
  if(users.findByUsername(username).isPresent())throw new ConflictException("用户名已存在");
  var selected=validateTreeIds(treeIds==null?List.of():treeIds);
  var actualRole=role==null?Role.MEMBER:role;
  if(actualRole==Role.ADMIN&&!selected.isEmpty())throw new ConflictException("管理员可访问全部家谱，无需分配家谱");
  var user=users.saveAndFlush(new User(username,encoder.encode(password),actualRole));
  selected.forEach(tree->grantEditor(tree,user.getId()));
  return dto(user);
 }
 @Transactional public UserDto enabled(Long id,boolean value,Authentication auth){
  var locked=users.findAllByOrderByIdAsc();var actor=access.requireAdmin(auth);var user=target(id,locked);
  if(!value)guardRemoval(user,actor,locked);
  user.setEnabled(value);users.saveAndFlush(user);return dto(user);
 }
 @Transactional public UserDto role(Long id,Role role,Authentication auth){
  var locked=users.findAllByOrderByIdAsc();var actor=access.requireAdmin(auth);var user=target(id,locked);
  if(role==null)throw new BadRequestException("role 不能为空");
  if(user.getId().equals(actor.getId()))throw new ConflictException("不能修改自己的角色");
  if(user.getRole()==Role.ADMIN&&role!=Role.ADMIN&&user.isEnabled()&&locked.stream().filter(u->u.getRole()==Role.ADMIN&&u.isEnabled()&&!u.isDeleted()).count()<=1)throw new ConflictException("不能降级最后一个可用管理员");
  // Global admin access is implicit: keep every stored grant for a later demotion.
  user.setRole(role);clearInaccessibleBinding(user);users.saveAndFlush(user);return dto(user);
 }
 @Transactional public void delete(Long id,Authentication auth){
  var locked=users.findAllByOrderByIdAsc();var actor=access.requireAdmin(auth);
  // Also accept legacy soft-deleted rows and repeated requests after a purge failure.
  var user=locked.stream().filter(u->u.getId().equals(id)).findFirst().orElseThrow(()->new NotFoundException("用户不存在"));
  guardRemoval(user,actor,locked);
  var bootstrap=properties.bootstrapAdmin();
  if(bootstrap!=null&&bootstrap.username()!=null&&bootstrap.username().equalsIgnoreCase(user.getUsername()))throw new ConflictException("不能删除配置中的初始化管理员，请先移除初始化管理员配置");
  // No original name (or recoverable password) is retained in the FK tombstone.
  user.setDeleted(true);user.setEnabled(false);user.setMemberId(null);user.setRole(Role.MEMBER);
  user.setUsername("deleted_"+id+"_"+UUID.randomUUID().toString().replace("-",""));
  user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()+UUID.randomUUID()));
  users.saveAndFlush(user);
  jdbc.sql("update users set display_name=null,birth_date=null,gender='UNKNOWN',portrait_stored_name=null,portrait_version=null,spouse_status=null,education=null,biography=null where id=:user").param("user",id).update();
  jdbc.sql("delete from tree_access where user_id=:user").param("user",id).update();
  jdbc.sql("delete from notifications where user_id=:user").param("user",id).update();
  // Shared tree owners/authors remain this anonymous id; do not transfer or delete history.
  // A failure here is AFTER the durable revocation. Retrying this DELETE retries purge.
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCommit(){portraits.purge(id);}
  });
 }
 @Transactional public UserDto assign(Long id,List<Long> treeIds,Authentication auth){
  var locked=users.findAllByOrderByIdAsc();access.requireAdmin(auth);var user=target(id,locked);
  if(user.getRole()==Role.ADMIN)throw new ConflictException("管理员可访问全部家谱，无需分配家谱");
  var selected=validateTreeIds(treeIds);
  var owned=trees.findByOwnerId(id).stream().map(FamilyTree::getId).toList();
  if(!selected.containsAll(owned))throw new ConflictException("选中集必须保留用户拥有的家谱，请先转移所有权或保留 owned 家谱");
  var existing=jdbc.sql("select tree_id from tree_access where user_id=:user").param("user",id).query(Long.class).list();
  for(var tree:existing)if(!selected.contains(tree))jdbc.sql("delete from tree_access where user_id=:user and tree_id=:tree").param("user",id).param("tree",tree).update();
  // Keep legitimate VIEWER/EDITOR/OWNER roles for retained grants; only new grants become EDITOR.
  for(var tree:selected)if(!existing.contains(tree)&&!owned.contains(tree))grantEditor(tree,id);
  clearInaccessibleBinding(user);users.saveAndFlush(user);
  return dto(user);
 }
 @Transactional public UserDto bindMember(Long id,Long memberId,Authentication auth){
  var locked=users.findAllByOrderByIdAsc();access.requireAdmin(auth);var user=target(id,locked);
  if(memberId!=null){
   if(memberId<=0)throw new BadRequestException("memberId 必须是正整数或 null");
   var tree=jdbc.sql("select tree_id from members where id=:id and deleted=false for update").param("id",memberId).query(Long.class).optional().orElseThrow(()->new NotFoundException("成员不存在或已删除"));
   if(user.getRole()!=Role.ADMIN&&!access.canRead(tree,user.getId()))throw new org.springframework.security.access.AccessDeniedException("目标账号没有此成员所属家谱的访问权限");
   if(locked.stream().anyMatch(other->!other.getId().equals(id)&&memberId.equals(other.getMemberId())))throw new ConflictException("此成员已绑定其他账号，请先解绑");
  }
  user.setMemberId(memberId);users.saveAndFlush(user);return dto(user);
 }
 @Transactional public void revokeTreeAccess(Long tree,Long userId,Authentication auth){
  var locked=users.findAllByOrderByIdAsc();access.requireEdit(tree,auth);
  jdbc.sql("delete from tree_access where tree_id=:tree and user_id=:user").param("tree",tree).param("user",userId).update();
  locked.stream().filter(u->u.getId().equals(userId)&&!u.isDeleted()).findFirst().ifPresent(user->{clearInaccessibleBinding(user);users.saveAndFlush(user);});
 }
 private void clearInaccessibleBinding(User user){var tree=access.memberTreeId(user);if(user.getMemberId()!=null&&(tree==null||(user.getRole()!=Role.ADMIN&&!access.canRead(tree,user.getId()))))user.setMemberId(null);}
 private User target(Long id,List<User> locked){return locked.stream().filter(u->u.getId().equals(id)&&!u.isDeleted()).findFirst().orElseThrow(()->new NotFoundException("用户不存在或已删除"));}
 private void guardRemoval(User user,User actor,List<User> locked){
  if(user.getId().equals(actor.getId()))throw new ConflictException("不能删除或停用自己的账号");
  if(user.getRole()==Role.ADMIN&&user.isEnabled()&&locked.stream().filter(u->u.getRole()==Role.ADMIN&&u.isEnabled()&&!u.isDeleted()).count()<=1)throw new ConflictException("不能删除或停用最后一个可用管理员");
 }
 private Set<Long> validateTreeIds(List<Long> ids){
  if(ids==null||ids.stream().anyMatch(id->id==null||id<=0))throw new BadRequestException("treeIds 必须为非空值数组，ID 必须是正整数（允许空数组）");
  var selected=new LinkedHashSet<>(ids);
  if(trees.findAllById(selected).size()!=selected.size())throw new NotFoundException("分配的家谱不存在");
  return selected;
 }
 private void grantEditor(Long tree,Long user){jdbc.sql("insert into tree_access(tree_id,user_id,access_role) values(:tree,:user,'EDITOR')").param("tree",tree).param("user",user).update();}
 private UserDto dto(User user){
  var ids=jdbc.sql("select id from family_trees where owner_id=:user union select tree_id from tree_access where user_id=:user order by 1").param("user",user.getId()).query(Long.class).list();
  var createdAt=user.getCreatedAt();
  if(createdAt==null)createdAt=jdbc.sql("select created_at from users where id=:id").param("id",user.getId()).query((rs,n)->rs.getTimestamp(1).toInstant()).single();
  var memberName=user.getMemberId()==null?null:jdbc.sql("select name from members where id=:id and deleted=false").param("id",user.getMemberId()).query(String.class).optional().orElse(null);
  return new UserDto(user.getId(),user.getUsername(),user.getRole(),user.isEnabled(),createdAt,ids,user.getMemberId(),access.memberTreeId(user),memberName);
 }
}
