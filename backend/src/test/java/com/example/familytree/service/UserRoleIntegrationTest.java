package com.example.familytree.service;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import com.example.familytree.service.AccessService.*;
import com.example.familytree.service.UserAdministrationService.UserDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserRoleIntegrationTest {
 private static final String PASSWORD="role-test-password-123";
 private static final String HASH=new BCryptPasswordEncoder(4).encode(PASSWORD);
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepo users;
 @Autowired TreeRepo trees;
 @Autowired MemberRepo members;
 @Autowired JdbcClient jdbc;
 @Autowired EntityManager em;
 @Autowired UserAdministrationService service;
 User admin,target,owner;
 FamilyTree owned,viewer,editor,grantedOwner,hidden;

 @BeforeEach void setup(){
  jdbc.sql("alter table users alter column created_at set default current_timestamp").update();
  admin=users.saveAndFlush(new User("role-admin",HASH,Role.ADMIN));
  target=users.saveAndFlush(new User("role-target",HASH,Role.MEMBER));
  owner=users.saveAndFlush(new User("role-owner",HASH,Role.MEMBER));
  owned=trees.saveAndFlush(new FamilyTree("role-owned","",target.getId()));
  viewer=trees.saveAndFlush(new FamilyTree("role-viewer","",owner.getId()));
  editor=trees.saveAndFlush(new FamilyTree("role-editor","",owner.getId()));
  grantedOwner=trees.saveAndFlush(new FamilyTree("role-granted-owner","",owner.getId()));
  hidden=trees.saveAndFlush(new FamilyTree("role-hidden","",owner.getId()));
  grant(viewer,"VIEWER");grant(editor,"EDITOR");grant(grantedOwner,"OWNER");
 }
 private void grant(FamilyTree tree,String role){jdbc.sql("insert into tree_access(tree_id,user_id,access_role) values(:t,:u,:r)").param("t",tree.getId()).param("u",target.getId()).param("r",role).update();}
 private Map<Long,String> grants(){
  var result=new LinkedHashMap<Long,String>();
  jdbc.sql("select tree_id,access_role from tree_access where user_id=:u order by tree_id").param("u",target.getId()).query((rs,n)->Map.entry(rs.getLong(1),rs.getString(2))).list().forEach(entry->result.put(entry.getKey(),entry.getValue()));
  return result;
 }
 private UsernamePasswordAuthenticationToken auth(User user){return new UsernamePasswordAuthenticationToken(user.getUsername(),"unused");}
 private UsernamePasswordAuthenticationToken forged(User user){return UsernamePasswordAuthenticationToken.authenticated(user.getUsername(),null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));}
 private MockHttpSession login(User user)throws Exception {
  var result=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",user.getUsername(),"password",PASSWORD))))
   .andExpect(status().isOk()).andReturn();
  return (MockHttpSession)result.getRequest().getSession(false);
 }
 private ResultActions change(Long id,Role role,MockHttpSession session)throws Exception {
  return mvc.perform(put("/api/v1/admin/users/{id}/role",id).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("role",role.name()))));
 }
 private Member person(FamilyTree tree,String name){return members.saveAndFlush(new Member(tree.getId(),name,Gender.UNKNOWN));}
 private void bind(Member person){service.bindMember(target.getId(),person.getId(),auth(admin));}
 private UserDto currentTarget(){return service.list(auth(admin)).stream().filter(u->u.id().equals(target.getId())).findFirst().orElseThrow();}

 @Test void onlyDatabaseAdminMayChangeRolesEvenWithForgedAuthorities()throws Exception {
  var before=grants();
  for(var role:List.of(Role.MEMBER,Role.FAMILY_ADMIN)){
   target.setRole(role);users.flush();
   change(owner.getId(),Role.ADMIN,login(target)).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/admin/users/{id}/role",owner.getId()).with(user(new com.example.familytree.config.AccountPrincipal(target.getId(),org.springframework.security.core.userdetails.User.withUsername(target.getUsername()).password(HASH).roles("ADMIN").build()))).with(csrf()).contentType("application/json").content("{\"role\":\"ADMIN\"}"))
    .andExpect(status().isForbidden());
   assertThrows(AccessDeniedException.class,()->service.role(owner.getId(),Role.ADMIN,forged(target)));
  }
  assertEquals(Role.MEMBER,users.findById(owner.getId()).orElseThrow().getRole());assertEquals(before,grants());
 }
 @Test void authenticationAndCsrfAreRequired()throws Exception {
  mvc.perform(put("/api/v1/admin/users/{id}/role",target.getId()).with(csrf()).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isUnauthorized());
  mvc.perform(put("/api/v1/admin/users/{id}/role",target.getId()).session(login(admin)).contentType("application/json").content("{\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  assertEquals(Role.MEMBER,target.getRole());
 }
 @Test void selfChangesIncludingSameRoleAndLastAdminDemotionAreRejected()throws Exception {
  var disabled=users.saveAndFlush(new User("role-disabled-admin",HASH,Role.ADMIN));disabled.setEnabled(false);
  var deleted=users.saveAndFlush(new User("role-deleted-admin",HASH,Role.ADMIN));deleted.setDeleted(true);users.flush();
  var session=login(admin);
  for(var role:Role.values()){
   change(admin.getId(),role,session).andExpect(status().isConflict());
   assertThrows(ConflictException.class,()->service.role(admin.getId(),role,auth(admin)));
  }
  assertEquals(Role.ADMIN,users.findById(admin.getId()).orElseThrow().getRole());
  assertEquals(1L,jdbc.sql("select count(*) from users where role='ADMIN' and enabled=true and deleted=false").query(Long.class).single());
 }
 @Test void selfChangeIsAlsoForbiddenWhenAnotherAvailableAdminExists()throws Exception {
  target.setRole(Role.ADMIN);users.flush();var session=login(admin);
  for(var role:Role.values())change(admin.getId(),role,session).andExpect(status().isConflict());
  assertEquals(Role.ADMIN,admin.getRole());
 }
 @Test void deletedAndMissingTargetsAreNotOperable()throws Exception {
  target.setDeleted(true);users.flush();var session=login(admin);var before=grants();
  for(var role:Role.values()){
   change(target.getId(),role,session).andExpect(status().isNotFound());
   assertThrows(NotFoundException.class,()->service.role(target.getId(),role,auth(admin)));
  }
  change(Long.MAX_VALUE,Role.ADMIN,session).andExpect(status().isNotFound());
  assertThrows(NotFoundException.class,()->service.role(Long.MAX_VALUE,Role.ADMIN,auth(admin)));
  assertEquals(Role.MEMBER,target.getRole());assertEquals(before,grants());
 }
 @Test void deletedOrDisabledActorCannotUseAnExistingAdminSessionOrDirectService()throws Exception {
  target.setRole(Role.ADMIN);users.flush();var deletedSession=login(target);
  target.setDeleted(true);users.flush();
  change(owner.getId(),Role.ADMIN,deletedSession).andExpect(status().isUnauthorized());assertTrue(deletedSession.isInvalid());
  assertThrows(DisabledException.class,()->service.role(owner.getId(),Role.ADMIN,forged(target)));
  target.setDeleted(false);users.flush();var disabledSession=login(target);target.setEnabled(false);users.flush();
  change(owner.getId(),Role.ADMIN,disabledSession).andExpect(status().isUnauthorized());assertTrue(disabledSession.isInvalid());
  assertThrows(DisabledException.class,()->service.role(owner.getId(),Role.ADMIN,forged(target)));
  assertEquals(Role.MEMBER,owner.getRole());
 }
 @Test void invalidRoleBodiesAndServiceNullAreRejectedWithoutChangingState()throws Exception {
  var session=login(admin);var before=grants();
  for(String body:List.of("{}","{\"role\":null}","{\"role\":\"SUPER_ADMIN\"}","{\"role\":\"\"}","{\"role\":{}}","{\"role\":[]}","null","[]","{","")){
   mvc.perform(put("/api/v1/admin/users/{id}/role",target.getId()).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  }
  assertThrows(BadRequestException.class,()->service.role(target.getId(),null,auth(admin)));
  assertEquals(Role.MEMBER,target.getRole());assertEquals(before,grants());
 }
 @Test void serviceRoleCyclesPreserveEveryGrantOwnedTreeAndReadableBinding(){
  grant(owned,"VIEWER");
  var bound=person(viewer,"role-bound-viewer");bind(bound);var before=grants();var original=currentTarget();
  assertEquals(4,original.assignedTreeIds().size());
  for(var role:List.of(Role.FAMILY_ADMIN,Role.ADMIN,Role.ADMIN,Role.MEMBER,Role.ADMIN,Role.FAMILY_ADMIN,Role.MEMBER)){
   var dto=service.role(target.getId(),role,auth(admin));
   assertEquals(role,dto.role());assertEquals(original.id(),dto.id());assertEquals(original.username(),dto.username());
   assertEquals(original.enabled(),dto.enabled());assertEquals(original.createdAt(),dto.createdAt());assertEquals(original.assignedTreeIds(),dto.assignedTreeIds());
   assertEquals(bound.getId(),dto.memberId());assertEquals(viewer.getId(),dto.memberTreeId());assertEquals(bound.getName(),dto.memberName());
   assertEquals(before,grants());assertEquals(target.getId(),trees.findById(owned.getId()).orElseThrow().getOwnerId());
  }
  em.clear();assertEquals(before,grants());assertEquals(Role.MEMBER,users.findById(target.getId()).orElseThrow().getRole());
 }
 @Test void httpReturnsUnchangedUserDtoShapeAndDoesNotMaterializeAdminAccess()throws Exception {
  var bound=person(owned,"role-owned-binding");bind(bound);var before=grants();var session=login(admin);
  var result=change(target.getId(),Role.ADMIN,session).andExpect(status().isOk())
   .andExpect(jsonPath("id").value(target.getId().intValue())).andExpect(jsonPath("username").value(target.getUsername()))
   .andExpect(jsonPath("role").value("ADMIN")).andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("createdAt").isNotEmpty())
   .andExpect(jsonPath("assignedTreeIds.length()").value(4)).andExpect(jsonPath("memberId").value(bound.getId().intValue()))
   .andExpect(jsonPath("memberTreeId").value(owned.getId().intValue())).andExpect(jsonPath("memberName").value(bound.getName()))
   .andExpect(jsonPath("passwordHash").doesNotExist()).andReturn();
  var fields=new HashSet<String>();json.readTree(result.getResponse().getContentAsString()).fieldNames().forEachRemaining(fields::add);
  assertEquals(Set.of("id","username","role","enabled","createdAt","assignedTreeIds","memberId","memberTreeId","memberName"),fields);
  assertEquals(before,grants());
  change(target.getId(),Role.MEMBER,session).andExpect(status().isOk()).andExpect(jsonPath("memberId").value(bound.getId().intValue()));
  assertEquals(before,grants());
 }
 @Test void oldSessionImmediatelyTracksMemberFamilyAdminAndAdminScope()throws Exception {
  var bound=person(viewer,"role-self");var other=person(viewer,"role-other");bind(bound);
  var old=login(target);var operator=login(admin);var before=grants();
  mvc.perform(get("/api/v1/admin/users").session(old)).andExpect(status().isForbidden());
  edit(viewer,other,old).andExpect(status().isForbidden());
  change(target.getId(),Role.FAMILY_ADMIN,operator).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("role").value("FAMILY_ADMIN"));
  edit(viewer,other,old).andExpect(status().isOk());
  mvc.perform(get("/api/v1/trees/{id}/members",hidden.getId()).session(old)).andExpect(status().isForbidden());
  change(target.getId(),Role.ADMIN,operator).andExpect(status().isOk());
  mvc.perform(get("/api/v1/admin/users").session(old)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/trees/{id}/members",hidden.getId()).session(old)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("role").value("ADMIN"));
  change(target.getId(),Role.FAMILY_ADMIN,operator).andExpect(status().isOk());
  change(owner.getId(),Role.ADMIN,old).andExpect(status().isForbidden());
  assertThrows(AccessDeniedException.class,()->service.role(owner.getId(),Role.ADMIN,forged(target)));
  mvc.perform(get("/api/v1/trees/{id}/members",hidden.getId()).session(old)).andExpect(status().isForbidden());
  edit(viewer,other,old).andExpect(status().isOk());
  change(target.getId(),Role.MEMBER,operator).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("role").value("MEMBER")).andExpect(jsonPath("memberId").value(bound.getId().intValue()));
  edit(viewer,bound,old).andExpect(status().isOk());edit(viewer,other,old).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/trees").session(old)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
   .andExpect(jsonPath("$[?(@.name=='role-owned')].accessRole").value(contains("OWNER")))
   .andExpect(jsonPath("$[?(@.name=='role-viewer')].accessRole").value(contains("VIEWER")))
   .andExpect(jsonPath("$[?(@.name=='role-editor')].accessRole").value(contains("EDITOR")))
   .andExpect(jsonPath("$[?(@.name=='role-granted-owner')].accessRole").value(contains("OWNER")))
   .andExpect(jsonPath("$[?(@.name=='role-hidden')]").isEmpty());
  assertEquals(before,grants());assertFalse(old.isInvalid());
 }
 private ResultActions edit(FamilyTree tree,Member person,MockHttpSession session)throws Exception {
  return mvc.perform(put("/api/v1/trees/{tree}/members/{id}",tree.getId(),person.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"role-updated\"}"));
 }
 @Test void demotionToEitherScopedRoleClearsInaccessibleBindingAndOldSession()throws Exception {
  var bound=person(hidden,"role-hidden-binding");var operator=login(admin);var old=login(target);var before=grants();
  for(var role:List.of(Role.MEMBER,Role.FAMILY_ADMIN)){
   change(target.getId(),Role.ADMIN,operator).andExpect(status().isOk());bind(bound);
   change(target.getId(),role,operator).andExpect(status().isOk()).andExpect(jsonPath("memberId").value(nullValue()))
    .andExpect(jsonPath("memberTreeId").value(nullValue())).andExpect(jsonPath("memberName").value(nullValue()));
   assertNull(users.findById(target.getId()).orElseThrow().getMemberId());
   mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("role").value(role.name())).andExpect(jsonPath("memberId").value(nullValue())).andExpect(jsonPath("memberTreeId").value(nullValue()));
   edit(hidden,bound,old).andExpect(status().isForbidden());assertEquals(before,grants());
  }
 }
 @Test void existingCleanupAlsoDropsDeletedMemberBindingOnRoleChange(){
  var bound=person(viewer,"role-deleted-binding");bind(bound);bound.setDeleted(true);members.flush();
  var dto=service.role(target.getId(),Role.ADMIN,auth(admin));
  assertNull(dto.memberId());assertNull(dto.memberTreeId());assertNull(dto.memberName());
  assertNull(users.findById(target.getId()).orElseThrow().getMemberId());
 }
 @Test void disabledTargetCanChangeRoleWithoutBeingEnabled()throws Exception {
  target.setRole(Role.ADMIN);target.setEnabled(false);users.flush();
  change(target.getId(),Role.FAMILY_ADMIN,login(admin)).andExpect(status().isOk()).andExpect(jsonPath("role").value("FAMILY_ADMIN")).andExpect(jsonPath("enabled").value(false));
  assertFalse(target.isEnabled());assertEquals(Role.ADMIN,admin.getRole());
 }
}
