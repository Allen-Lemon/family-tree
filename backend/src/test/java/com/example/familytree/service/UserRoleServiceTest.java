package com.example.familytree.service;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import com.example.familytree.service.AccessService.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserRoleServiceTest {
 private final UserRepo users=mock(UserRepo.class);
 private final TreeRepo trees=mock(TreeRepo.class);
 private final AccessService access=mock(AccessService.class);
 private final JdbcClient jdbc=mock(JdbcClient.class);
 private final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken("role-actor","unused");
 private final UserAdministrationService service=new UserAdministrationService(users,trees,access,mock(PasswordEncoder.class),jdbc,mock(ProfilePortraitStorage.class),new com.example.familytree.config.AppProperties("unused",1,null,null,null));
 private User actor,target;

 @BeforeEach void setup(){
  actor=user(1L,Role.ADMIN);target=user(2L,Role.MEMBER);
  when(users.findAllByOrderByIdAsc()).thenReturn(List.of(actor,target));
  when(access.requireAdmin(auth)).thenReturn(actor);
 }
 private User user(Long id,Role role){var user=new User("role-user-"+id,"unused",role);user.setId(id);return user;}
 private void unchanged(){verify(users,never()).saveAndFlush(any(User.class));verifyNoInteractions(jdbc,trees);}

 @Test void locksAccountsInRepositoryOrderBeforeLiveAdminCheck(){
  doThrow(new AccessDeniedException("not admin")).when(access).requireAdmin(auth);
  assertThrows(AccessDeniedException.class,()->service.role(target.getId(),Role.ADMIN,auth));
  var order=inOrder(users,access);
  order.verify(users).findAllByOrderByIdAsc();order.verify(access).requireAdmin(auth);
  assertEquals(Role.MEMBER,target.getRole());unchanged();
 }
 @Test void directServiceCallRejectsNullRole(){
  assertThrows(BadRequestException.class,()->service.role(target.getId(),null,auth));
  assertEquals(Role.MEMBER,target.getRole());unchanged();
 }
 @Test void selfRoleChangeIsRejectedEvenForTheSameRole(){
  for(var role:Role.values())assertThrows(ConflictException.class,()->service.role(actor.getId(),role,auth));
  assertEquals(Role.ADMIN,actor.getRole());unchanged();
 }
 @Test void missingAndDeletedTargetsCannotBeChanged(){
  target.setDeleted(true);
  for(var role:Role.values())assertThrows(NotFoundException.class,()->service.role(target.getId(),role,auth));
  assertThrows(NotFoundException.class,()->service.role(Long.MAX_VALUE,Role.ADMIN,auth));
  assertEquals(Role.MEMBER,target.getRole());unchanged();
 }
 @Test void deletionKeepsSelfAndLastAvailableAdminGuardsBeforeAnyPrivateDataChange(){
  assertThrows(ConflictException.class,()->service.delete(actor.getId(),auth));
  target.setRole(Role.ADMIN);
  var disabled=user(3L,Role.ADMIN);disabled.setEnabled(false);
  var deleted=user(4L,Role.ADMIN);deleted.setDeleted(true);
  when(users.findAllByOrderByIdAsc()).thenReturn(List.of(target,disabled,deleted));
  assertThrows(ConflictException.class,()->service.delete(target.getId(),auth));
  assertFalse(target.isDeleted());assertTrue(target.isEnabled());assertEquals(Role.ADMIN,target.getRole());unchanged();
 }
 @Test void lastAvailableAdminGuardIgnoresDisabledAndDeletedAdministrators(){
  target.setRole(Role.ADMIN);
  var disabled=user(3L,Role.ADMIN);disabled.setEnabled(false);
  var deleted=user(4L,Role.ADMIN);deleted.setDeleted(true);
  // Isolate the defensive invariant: a normal authorized actor is itself another
  // available admin, so the only reachable last-admin HTTP case is also self.
  when(users.findAllByOrderByIdAsc()).thenReturn(List.of(target,disabled,deleted));
  for(var role:List.of(Role.MEMBER,Role.FAMILY_ADMIN))assertThrows(ConflictException.class,()->service.role(target.getId(),role,auth));
  assertEquals(Role.ADMIN,target.getRole());unchanged();
 }
}
