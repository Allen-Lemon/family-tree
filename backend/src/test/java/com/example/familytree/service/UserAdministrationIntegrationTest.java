package com.example.familytree.service;

import com.example.familytree.config.AppProperties;
import com.example.familytree.config.BootstrapAdmin;
import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserAdministrationIntegrationTest {
 private static final String PASSWORD="test-password-123";
 private static final String HASH=new BCryptPasswordEncoder(4).encode(PASSWORD);
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired UserRepo users;
 @Autowired TreeRepo trees;
 @Autowired MemberRepo members;
 @Autowired StoryRepo stories;
 @Autowired EventRepo events;
 @Autowired MediaRepo media;
 @Autowired JdbcClient jdbc;
 @Autowired EntityManager em;
 @Autowired UserAdministrationService adminService;
 @Autowired FamilyService family;
 @Autowired PasswordEncoder encoder;
 @Autowired AppProperties properties;
 @Autowired AccessService access;
 @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ProfilePortraitStorage portraits;
 User admin,owner,member;
 FamilyTree owned,shared,secret;
 @BeforeEach void setup(){
  jdbc.sql("alter table users alter column created_at set default current_timestamp").update();
  admin=users.saveAndFlush(new User("account-admin",HASH,Role.ADMIN));
  owner=users.saveAndFlush(new User("account-owner",HASH,Role.MEMBER));
  member=users.saveAndFlush(new User("account-member",HASH,Role.MEMBER));
  owned=trees.saveAndFlush(new FamilyTree("owned-tree","",member.getId()));
  shared=trees.saveAndFlush(new FamilyTree("shared-tree","",owner.getId()));
  secret=trees.saveAndFlush(new FamilyTree("secret-tree","",owner.getId()));
 }
 MockHttpSession login(User user)throws Exception {
  var result=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",user.getUsername(),"password",PASSWORD))))
   .andExpect(status().isOk()).andExpect(jsonPath("role").value(user.getRole().name())).andReturn();
  return (MockHttpSession)result.getRequest().getSession(false);
 }
 void grant(FamilyTree tree,User user,String role){jdbc.sql("insert into tree_access(tree_id,user_id,access_role) values(:t,:u,:r)").param("t",tree.getId()).param("u",user.getId()).param("r",role).update();}
 String assignment(Long...ids)throws Exception{return json.writeValueAsString(Map.of("treeIds",List.of(ids)));}
 UsernamePasswordAuthenticationToken auth(User user){return new UsernamePasswordAuthenticationToken(user.getUsername(),"unused");}
 @Test void deleteAnonymizesPrivateDataPreservesHistoryAndAllowsSafeUsernameReuse()throws Exception {
  var oldUsername=member.getUsername();
  grant(shared,member,"EDITOR");
  jdbc.sql("update users set display_name='private name',birth_date='1990-01-02',gender='FEMALE',portrait_stored_name='private-key',portrait_version='private-version',spouse_status='private spouse',education='private education',biography='private biography' where id=:u").param("u",member.getId()).update();
  jdbc.sql("update users set display_name='other private profile',biography='other biography' where id=:u").param("u",owner.getId()).update();
  jdbc.sql("insert into notifications(user_id,tree_id,type,message) values(:u,:t,'PRIVATE','private notification')").param("u",member.getId()).param("t",owned.getId()).update();
  jdbc.sql("insert into notifications(user_id,tree_id,type,message) values(:u,:t,'PRIVATE','other notification')").param("u",owner.getId()).param("t",shared.getId()).update();
  var person=members.saveAndFlush(new Member(owned.getId(),"historical-person",Gender.UNKNOWN));
  var story=new Story();story.setTreeId(owned.getId());story.setAuthorId(member.getId());story.setMemberId(person.getId());story.setTitle("history");story.setContent("retained");story=stories.saveAndFlush(story);
  var event=new FamilyEvent();event.setTreeId(owned.getId());event.setCreatedBy(member.getId());event.setTitle("event");event.setEventType("OTHER");event.setEventDate("2000");event.setDatePrecision(DatePrecision.YEAR);event=events.saveAndFlush(event);
  var asset=new Media();asset.setTreeId(owned.getId());asset.setUploaderId(member.getId());asset.setStoredName("historical-media");asset=media.saveAndFlush(asset);
  var otherPerson=members.saveAndFlush(new Member(owned.getId(),"other historical-person",Gender.FEMALE));
  jdbc.sql("insert into relationships(tree_id,from_member_id,to_member_id,relation_type,version) values(:t,:f,:x,'SIBLING',0)").param("t",owned.getId()).param("f",person.getId()).param("x",otherPerson.getId()).update();
  var historicalSession=login(member);
  var legacySession=login(member);
  var legacyContext=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
  var legacyPrincipal=org.springframework.security.core.userdetails.User.withUsername(oldUsername).password(HASH).roles("MEMBER").build();
  legacyContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(legacyPrincipal,null,legacyPrincipal.getAuthorities()));
  legacySession.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,legacyContext);
  var stablePrincipal=(com.example.familytree.config.AccountPrincipal)((org.springframework.security.core.context.SecurityContext)historicalSession.getAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).getAuthentication().getPrincipal();
  var adminSession=login(admin);
  mvc.perform(delete("/api/v1/admin/users/{id}",member.getId()).session(adminSession).with(csrf())).andExpect(status().isNoContent());
  em.clear();
  var retained=users.findById(member.getId()).orElseThrow();assertTrue(retained.isDeleted());assertFalse(retained.isEnabled());
  assertTrue(retained.getUsername().startsWith("deleted_"+member.getId()+"_"));assertFalse(retained.getUsername().contains(oldUsername));assertTrue(retained.getUsername().length()<=80);
  assertEquals(Role.MEMBER,retained.getRole());assertNull(retained.getMemberId());assertFalse(encoder.matches(PASSWORD,retained.getPasswordHash()));
  assertNull(retained.getDisplayName());assertNull(retained.getProfileBirthDate());assertEquals(Gender.UNKNOWN,retained.getProfileGender());assertNull(retained.getPortraitStoredName());assertNull(retained.getPortraitVersion());
  assertNull(retained.getSpouseStatus());assertNull(retained.getEducation());assertNull(retained.getBiography());
  assertEquals("other private profile",users.findById(owner.getId()).orElseThrow().getDisplayName());assertEquals("other biography",users.findById(owner.getId()).orElseThrow().getBiography());
  assertEquals(0,jdbc.sql("select count(*) from notifications where user_id=:u").param("u",member.getId()).query(Integer.class).single());
  assertEquals(1,jdbc.sql("select count(*) from notifications where user_id=:u").param("u",owner.getId()).query(Integer.class).single());
  assertEquals(member.getId(),trees.findById(owned.getId()).orElseThrow().getOwnerId());
  assertTrue(members.existsById(person.getId()));assertTrue(stories.existsById(story.getId()));assertTrue(events.existsById(event.getId()));assertTrue(media.existsById(asset.getId()));
  assertEquals(0,jdbc.sql("select count(*) from tree_access where user_id=:u").param("u",member.getId()).query(Integer.class).single());
  assertEquals("retained",stories.findById(story.getId()).orElseThrow().getContent());assertEquals(member.getId(),stories.findById(story.getId()).orElseThrow().getAuthorId());
  assertEquals(member.getId(),events.findById(event.getId()).orElseThrow().getCreatedBy());assertEquals("historical-media",media.findById(asset.getId()).orElseThrow().getStoredName());
  assertEquals(1,jdbc.sql("select count(*) from relationships where tree_id=:t").param("t",owned.getId()).query(Integer.class).single());
  mvc.perform(get("/api/v1/admin/users").session(adminSession)).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.username=='account-member')]").isEmpty()).andExpect(jsonPath("$[0].passwordHash").doesNotExist());
  mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",oldUsername,"password",PASSWORD)))).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/v1/admin/users").session(adminSession).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",oldUsername,"password",PASSWORD)))).andExpect(status().isOk());
  var replacement=users.findByUsername(oldUsername).orElseThrow();assertNotEquals(member.getId(),replacement.getId());
  mvc.perform(get("/api/v1/trees").session(historicalSession)).andExpect(status().isUnauthorized());assertTrue(historicalSession.isInvalid());
  mvc.perform(get("/api/v1/auth/me").session(legacySession)).andExpect(status().isUnauthorized());assertTrue(legacySession.isInvalid());
  assertThrows(org.springframework.security.authentication.BadCredentialsException.class,()->access.current(UsernamePasswordAuthenticationToken.authenticated(stablePrincipal,null,stablePrincipal.getAuthorities())));
  mvc.perform(get("/api/v1/trees").session(login(replacement))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  mvc.perform(put("/api/v1/admin/users/{id}/enabled",member.getId()).param("value","true").session(adminSession).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/trees/{tree}/members",owned.getId()).session(adminSession)).andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("historical-person"));
 }
 @Test void portraitPurgeFailureReportsCommittedRevocationAndDeleteRetryRemovesAllOwnFiles()throws Exception {
  // Commit fixtures so MVC exercises the real service commit, not a test rollback.
  org.springframework.test.context.transaction.TestTransaction.flagForCommit();
  org.springframework.test.context.transaction.TestTransaction.end();
  var root=java.nio.file.Path.of(properties.storageRoot()).toAbsolutePath().normalize().resolve("profile-portraits");
  java.nio.file.Files.createDirectories(root);
  var current=root.resolve(member.getId()+"_current.png");var orphan=root.resolve(member.getId()+"_historical-orphan.jpg");var other=root.resolve(owner.getId()+"_keep.png");
  try {
   java.nio.file.Files.write(current,new byte[]{1});java.nio.file.Files.write(orphan,new byte[]{2});java.nio.file.Files.write(other,new byte[]{3});
   jdbc.sql("update users set portrait_stored_name=:p,portrait_version='version',display_name='private' where id=:u").param("p",current.getFileName().toString()).param("u",member.getId()).update();
   var session=login(admin);
   org.mockito.Mockito.doThrow(new ProfilePortraitStorage.PortraitPurgeException()).doCallRealMethod().when(portraits).purge(member.getId());
   mvc.perform(delete("/api/v1/admin/users/{id}",member.getId()).session(session).with(csrf())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("PRIVATE_PORTRAIT_PURGE_PENDING"));
   assertTrue(jdbc.sql("select deleted from users where id=:u").param("u",member.getId()).query(Boolean.class).single());
   assertEquals(1,jdbc.sql("select count(*) from users where id=:u and display_name is null and portrait_stored_name is null and enabled=false").param("u",member.getId()).query(Integer.class).single());
   assertTrue(java.nio.file.Files.exists(current));assertTrue(java.nio.file.Files.exists(orphan));
   mvc.perform(delete("/api/v1/admin/users/{id}",member.getId()).session(session).with(csrf())).andExpect(status().isNoContent());
   assertFalse(java.nio.file.Files.exists(current));assertFalse(java.nio.file.Files.exists(orphan));assertTrue(java.nio.file.Files.exists(other));
   assertEquals(member.getId(),jdbc.sql("select owner_id from family_trees where id=:t").param("t",owned.getId()).query(Long.class).single());
  } finally {
   org.mockito.Mockito.doCallRealMethod().when(portraits).purge(member.getId());
   java.nio.file.Files.deleteIfExists(current);java.nio.file.Files.deleteIfExists(orphan);java.nio.file.Files.deleteIfExists(other);
   for(var tree:List.of(owned,shared,secret))jdbc.sql("delete from family_trees where id=:t").param("t",tree.getId()).update();
   for(var user:List.of(admin,owner,member))jdbc.sql("delete from users where id=:u").param("u",user.getId()).update();
  }
 }
 @Test void legacyDeletedAccountsCanBeAnonymizedAndPurgeWaitsForCommit(){
  member.setDeleted(true);member.setEnabled(false);users.flush();
  adminService.delete(member.getId(),auth(admin));
  assertTrue(member.getUsername().startsWith("deleted_"+member.getId()+"_"));assertEquals(Role.MEMBER,member.getRole());
  org.mockito.Mockito.verify(portraits,org.mockito.Mockito.never()).purge(member.getId());
 }
 @Test void configuredBootstrapAccountCannotBeDeleted(){
  var props=new AppProperties("unused",1,null,new AppProperties.BootstrapAdmin(member.getUsername(),PASSWORD),null);
  var service=new UserAdministrationService(users,trees,access,encoder,jdbc,portraits,props);
  assertThrows(AccessService.ConflictException.class,()->service.delete(member.getId(),auth(admin)));
  assertFalse(member.isDeleted());assertEquals("account-member",member.getUsername());
 }
 @Test void selfAndLastAvailableAdminCannotBeDeletedOrDisabled()throws Exception {
  var disabled=users.saveAndFlush(new User("disabled-admin",HASH,Role.ADMIN));disabled.setEnabled(false);users.flush();
  var session=login(admin);
  mvc.perform(delete("/api/v1/admin/users/{id}",admin.getId()).session(session).with(csrf())).andExpect(status().isConflict());
  mvc.perform(put("/api/v1/admin/users/{id}/enabled",admin.getId()).param("value","false").session(session).with(csrf())).andExpect(status().isConflict());
  assertTrue(users.findById(admin.getId()).orElseThrow().isEnabled());
 }
 @Test void anotherAdministratorCanBeDeletedWithoutDeletingItsOwnedTree()throws Exception {
  var other=users.saveAndFlush(new User("other-admin",HASH,Role.ADMIN));var tree=trees.saveAndFlush(new FamilyTree("admin-history","",other.getId()));
  mvc.perform(delete("/api/v1/admin/users/{id}",other.getId()).session(login(admin)).with(csrf())).andExpect(status().isNoContent());
  assertEquals(other.getId(),trees.findById(tree.getId()).orElseThrow().getOwnerId());
 }
 @Test void disablingAccountInvalidatesItsOldSessionEvenBeforeCsrf()throws Exception {
  var old=login(member);
  mvc.perform(put("/api/v1/admin/users/{id}/enabled",member.getId()).param("value","false").session(login(admin)).with(csrf())).andExpect(status().isOk());
  mvc.perform(post("/api/v1/trees").session(old).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isUnauthorized());assertTrue(old.isInvalid());
 }
 @Test void invalidOldSessionsCannotBypassPublicEndpointsAndFreshLoginRecovers()throws Exception {
  var disabledCsrf=login(member);var disabledLogin=login(member);
  member.setEnabled(false);users.flush();
  mvc.perform(get("/api/v1/auth/csrf").session(disabledCsrf)).andExpect(status().isUnauthorized());assertTrue(disabledCsrf.isInvalid());
  mvc.perform(post("/api/v1/auth/login").session(disabledLogin).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",admin.getUsername(),"password",PASSWORD)))).andExpect(status().isUnauthorized());assertTrue(disabledLogin.isInvalid());
  var deletedCsrf=login(owner);var deletedLogin=login(owner);owner.setDeleted(true);owner.setEnabled(false);users.flush();
  mvc.perform(get("/api/v1/auth/csrf").session(deletedCsrf)).andExpect(status().isUnauthorized());assertTrue(deletedCsrf.isInvalid());
  mvc.perform(post("/api/v1/auth/login").session(deletedLogin).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",admin.getUsername(),"password",PASSWORD)))).andExpect(status().isUnauthorized());assertTrue(deletedLogin.isInvalid());
  // Once invalidated, the old browser cookie no longer identifies a session. Anonymous CSRF and a fresh login work.
  mvc.perform(get("/api/v1/auth/csrf").cookie(new jakarta.servlet.http.Cookie("JSESSIONID",deletedLogin.getId()))).andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty());
  var fresh=login(admin);mvc.perform(get("/api/v1/auth/me").session(fresh)).andExpect(status().isOk()).andExpect(jsonPath("role").value("ADMIN"));
 }
 @Test void physicallyMissingAccountInvalidatesSession()throws Exception {
  var temporary=users.saveAndFlush(new User("temporary-user",HASH,Role.MEMBER));var session=login(temporary);
  users.delete(temporary);users.flush();
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isUnauthorized());assertTrue(session.isInvalid());
 }
 @Test void demotedAdminCannotUseStaleSessionAuthoritiesAndMeUsesDatabaseRole()throws Exception {
  var session=login(admin);admin.setRole(Role.MEMBER);users.flush();
  mvc.perform(get("/api/v1/admin/users").session(session)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("role").value("MEMBER"));
  mvc.perform(get("/api/v1/trees").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
 }
 @Test void promotedMemberGetsCurrentDatabaseAuthorities()throws Exception {
  var session=login(member);member.setRole(Role.ADMIN);users.flush();
  mvc.perform(get("/api/v1/admin/users").session(session)).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("role").value("ADMIN"));
 }
 @Test void membersCannotCallAdminViaHttpOrServiceWithForgedRole()throws Exception {
  var session=login(member);
  mvc.perform(get("/api/v1/admin/users").session(session)).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/admin/users/{id}",owner.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
  var forged=UsernamePasswordAuthenticationToken.authenticated(member.getUsername(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
  assertThrows(AccessDeniedException.class,()->adminService.list(forged));
  assertThrows(AccessDeniedException.class,()->adminService.create("not-created",PASSWORD,Role.ADMIN,List.of(),forged));
  assertThrows(AccessDeniedException.class,()->adminService.assign(owner.getId(),List.of(),forged));
  assertThrows(AccessDeniedException.class,()->adminService.enabled(owner.getId(),false,forged));
  assertThrows(AccessDeniedException.class,()->adminService.delete(owner.getId(),forged));
  assertThrows(AccessDeniedException.class,()->adminService.bindMember(owner.getId(),null,forged));
 }
 @Test void adminSeesAllTreesAndMemberOnlyOwnedAndGrantedWithActualAccessRole()throws Exception {
  grant(shared,member,"VIEWER");
  mvc.perform(get("/api/v1/trees").session(login(admin))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)).andExpect(jsonPath("$[0].accessRole").value("OWNER"));
  mvc.perform(get("/api/v1/trees").session(login(member))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[?(@.name=='owned-tree')].accessRole").value(org.hamcrest.Matchers.contains("OWNER"))).andExpect(jsonPath("$[?(@.name=='shared-tree')].accessRole").value(org.hamcrest.Matchers.contains("VIEWER"))).andExpect(jsonPath("$[?(@.name=='secret-tree')]").isEmpty());
 }
 @Test void noTreeAccessCannotReadWriteOrExportHiddenTree()throws Exception {
  var hidden=members.saveAndFlush(new Member(secret.getId(),"TOP-SECRET",Gender.UNKNOWN));var session=login(member);
  for(String suffix:List.of("/members","/relationships","/stories","/events","/search?q=TOP","/statistics","/backups","/exports/sync?format=JSON","/members/"+hidden.getId())){
   mvc.perform(get("/api/v1/trees/"+secret.getId()+suffix).session(session)).andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("TOP-SECRET"))));
  }
  mvc.perform(post("/api/v1/trees/{tree}/members",secret.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"intruder\"}")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",secret.getId(),hidden.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
 }
 @Test void cannotSmuggleForeignMemberOrMediaIntoAccessibleTree()throws Exception {
  var hidden=members.saveAndFlush(new Member(secret.getId(),"hidden",Gender.UNKNOWN));var asset=new Media();asset.setTreeId(secret.getId());asset.setUploaderId(owner.getId());asset.setStoredName("secret-asset");asset=media.saveAndFlush(asset);var session=login(admin);
  mvc.perform(get("/api/v1/trees/{tree}/members/{id}",owned.getId(),hidden.getId()).session(session)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/trees/{tree}/media/{id}",owned.getId(),asset.getId()).session(session)).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/trees/{tree}/custom-fields/members/{id}",owned.getId(),hidden.getId()).session(session)).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/trees/{tree}/members",owned.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("name","bad","avatarMediaId",asset.getId())))).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/trees/{tree}/stories",owned.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("title","bad","content","bad","memberId",hidden.getId())))).andExpect(status().isNotFound());
  mvc.perform(post("/api/v1/trees/{tree}/events",owned.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("title","bad","memberId",hidden.getId())))).andExpect(status().isNotFound());
 }
 @Test void viewerAndEditorCannotCreateMembers()throws Exception {
  grant(shared,member,"VIEWER");var session=login(member);
  mvc.perform(get("/api/v1/trees/{tree}/members",shared.getId()).session(session)).andExpect(status().isOk());
  mvc.perform(post("/api/v1/trees/{tree}/members",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"new member\"}")).andExpect(status().isForbidden());
  jdbc.sql("update tree_access set access_role='EDITOR' where tree_id=:t and user_id=:u").param("t",shared.getId()).param("u",member.getId()).update();
  mvc.perform(post("/api/v1/trees/{tree}/members",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"new member\"}")).andExpect(status().isForbidden());
 }
 @Test void creationAssignsEditorAndDtoListsDistinctOwnedAndAccessTrees()throws Exception {
  var session=login(admin);
  var result=mvc.perform(post("/api/v1/admin/users").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username","new-editor","password",PASSWORD,"treeIds",List.of(shared.getId(),shared.getId()))))).andExpect(status().isOk()).andExpect(jsonPath("role").value("MEMBER")).andExpect(jsonPath("assignedTreeIds").value(org.hamcrest.Matchers.contains(shared.getId().intValue()))).andExpect(jsonPath("passwordHash").doesNotExist()).andReturn();
  var id=json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  assertEquals("EDITOR",jdbc.sql("select access_role from tree_access where user_id=:u").param("u",id).query(String.class).single());
  grant(owned,member,"VIEWER");grant(shared,member,"EDITOR");
  var dto=adminService.list(auth(admin)).stream().filter(x->x.id().equals(member.getId())).findFirst().orElseThrow();
  assertEquals(Set.of(owned.getId(),shared.getId()),new HashSet<>(dto.assignedTreeIds()));assertEquals(2,dto.assignedTreeIds().size());
 }
 @Test void assignmentPreservesRetainedViewerAndEditorRolesAndRemovesOnlyDeselectedGrants()throws Exception {
  grant(shared,member,"VIEWER");grant(secret,member,"EDITOR");var extra=trees.saveAndFlush(new FamilyTree("newly-granted","",owner.getId()));var session=login(admin);
  mvc.perform(put("/api/v1/admin/users/{id}/trees",member.getId()).session(session).with(csrf()).contentType("application/json").content(assignment(owned.getId(),shared.getId(),secret.getId(),extra.getId()))).andExpect(status().isOk());
  assertEquals("VIEWER",jdbc.sql("select access_role from tree_access where user_id=:u and tree_id=:t").param("u",member.getId()).param("t",shared.getId()).query(String.class).single());
  assertEquals("EDITOR",jdbc.sql("select access_role from tree_access where user_id=:u and tree_id=:t").param("u",member.getId()).param("t",secret.getId()).query(String.class).single());
  mvc.perform(put("/api/v1/admin/users/{id}/trees",member.getId()).session(session).with(csrf()).contentType("application/json").content(assignment(owned.getId(),extra.getId()))).andExpect(status().isOk());
  assertEquals(List.of(extra.getId()),jdbc.sql("select tree_id from tree_access where user_id=:u").param("u",member.getId()).query(Long.class).list());
 }
 @Test void assignmentCannotSilentlyRemoveOwnedTreeAndRollsBackOnInvalidIds()throws Exception {
  grant(shared,member,"VIEWER");var session=login(admin);
  mvc.perform(put("/api/v1/admin/users/{id}/trees",member.getId()).session(session).with(csrf()).contentType("application/json").content(assignment())).andExpect(status().isConflict()).andExpect(jsonPath("message").value(org.hamcrest.Matchers.containsString("所有权")));
  mvc.perform(put("/api/v1/admin/users/{id}/trees",member.getId()).session(session).with(csrf()).contentType("application/json").content(assignment(owned.getId(),Long.MAX_VALUE))).andExpect(status().isNotFound());
  assertEquals(List.of(shared.getId()),jdbc.sql("select tree_id from tree_access where user_id=:u").param("u",member.getId()).query(Long.class).list());
 }
 @Test void emptyAssignmentCanClearNonOwnerAndExistingSessionImmediatelyLosesAccess()throws Exception {
  grant(shared,admin,"VIEWER");grant(shared,owner,"EDITOR");
  var noOwned=users.saveAndFlush(new User("non-owner",HASH,Role.MEMBER));grant(shared,noOwned,"EDITOR");var old=login(noOwned);
  mvc.perform(put("/api/v1/admin/users/{id}/trees",noOwned.getId()).session(login(admin)).with(csrf()).contentType("application/json").content(assignment())).andExpect(status().isOk()).andExpect(jsonPath("assignedTreeIds").isEmpty());
  mvc.perform(get("/api/v1/trees/{tree}/members",shared.getId()).session(old)).andExpect(status().isForbidden());
 }
 @Test void validationRejectsMissingNullNegativeAndUnknownTreeAssignments()throws Exception {
  var session=login(admin);
  for(String body:List.of("{}","{\"treeIds\":null}","{\"treeIds\":[null]}","{\"treeIds\":[-1]}"))mvc.perform(put("/api/v1/admin/users/{id}/trees",member.getId()).session(session).with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
  mvc.perform(post("/api/v1/admin/users").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username","invalid-creation","password",PASSWORD,"treeIds",List.of(Long.MAX_VALUE))))).andExpect(status().isNotFound());
  assertTrue(users.findByUsername("invalid-creation").isEmpty());
 }
 @Test void administratorAssignmentsExplicitlyRejected()throws Exception {
  var session=login(admin);
  mvc.perform(put("/api/v1/admin/users/{id}/trees",admin.getId()).session(session).with(csrf()).contentType("application/json").content(assignment())).andExpect(status().isConflict());
  mvc.perform(post("/api/v1/admin/users").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username","invalid-admin","password",PASSWORD,"role","ADMIN","treeIds",List.of(shared.getId()))))).andExpect(status().isConflict());
  assertTrue(users.findByUsername("invalid-admin").isEmpty());
 }
 @Test void deletedAccountIsAbsentFromAccessListAndCannotBeGrantedAgain()throws Exception {
  grant(shared,member,"VIEWER");var session=login(admin);
  mvc.perform(delete("/api/v1/admin/users/{id}",member.getId()).session(session).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/v1/trees/{tree}/access",shared.getId()).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
  mvc.perform(put("/api/v1/trees/{tree}/access",shared.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",member.getUsername(),"role","EDITOR")))).andExpect(status().isNotFound());
 }
 void bind(User user,Member person,MockHttpSession session)throws Exception {
  mvc.perform(put("/api/v1/admin/users/{id}/member",user.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("memberId",person.getId()))))
   .andExpect(status().isOk()).andExpect(jsonPath("memberId").value(person.getId().intValue())).andExpect(jsonPath("memberTreeId").value(person.getTreeId().intValue())).andExpect(jsonPath("memberName").value(person.getName()));
 }
 @Test void existingSessionSeesBindingAndOnlySelfBasicFieldsCanBeUpdated()throws Exception {
  grant(shared,member,"VIEWER");
  var self=members.saveAndFlush(new Member(shared.getId(),"self",Gender.UNKNOWN));var other=members.saveAndFlush(new Member(shared.getId(),"other",Gender.UNKNOWN));
  var asset=new Media();asset.setTreeId(shared.getId());asset.setUploaderId(admin.getId());asset.setStoredName("self-avatar");asset=media.saveAndFlush(asset);self.setAvatarMediaId(asset.getId());self.setSensitiveJson("private");members.flush();
  var old=login(member);var session=login(admin);bind(member,self,session);
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(status().isOk()).andExpect(jsonPath("memberId").value(self.getId().intValue())).andExpect(jsonPath("memberTreeId").value(shared.getId().intValue()));
  var fresh=login(member);mvc.perform(get("/api/v1/auth/me").session(fresh)).andExpect(jsonPath("memberId").value(self.getId().intValue()));
  var payload=new LinkedHashMap<String,Object>();payload.put("name","updated-self");payload.put("gender","FEMALE");payload.put("birthDate","1990-02-03");payload.put("address","new-address");payload.put("id",other.getId());payload.put("treeId",secret.getId());payload.put("role","ADMIN");payload.put("memberId",other.getId());payload.put("sensitiveJson","injected");
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),self.getId()).session(old).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload)))
   .andExpect(status().isOk()).andExpect(jsonPath("id").value(self.getId().intValue())).andExpect(jsonPath("treeId").value(shared.getId().intValue())).andExpect(jsonPath("name").value("updated-self")).andExpect(jsonPath("avatarMediaId").value(asset.getId().intValue()));
  assertEquals("private",members.findById(self.getId()).orElseThrow().getSensitiveJson());assertEquals(Role.MEMBER,users.findById(member.getId()).orElseThrow().getRole());
  payload.put("avatarMediaId",Long.MAX_VALUE);
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),self.getId()).session(old).with(csrf()).contentType("application/json").content(json.writeValueAsString(payload))).andExpect(status().isOk()).andExpect(jsonPath("avatarMediaId").value(asset.getId().intValue()));
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),other.getId()).session(old).with(csrf()).contentType("application/json").content("{\"name\":\"forbidden\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",owned.getId(),self.getId()).session(old).with(csrf()).contentType("application/json").content("{\"name\":\"wrong-tree\"}")).andExpect(status().isNotFound());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",secret.getId(),self.getId()).session(old).with(csrf()).contentType("application/json").content("{\"name\":\"hidden-tree\"}")).andExpect(status().isForbidden());
 }
 @Test void databaseBindingRevocationAndDeletedMemberBeatStaleManagedEntities()throws Exception {
  grant(shared,member,"EDITOR");var self=members.saveAndFlush(new Member(shared.getId(),"self",Gender.UNKNOWN));var other=members.saveAndFlush(new Member(shared.getId(),"other",Gender.UNKNOWN));var session=login(member);var adminSession=login(admin);bind(member,self,adminSession);
  // Deliberately leave the managed user/member stale, as a long-lived session/cache must never grant permission.
  jdbc.sql("update users set member_id=:m where id=:u").param("m",other.getId()).param("u",member.getId()).update();
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("memberId").value(other.getId().intValue()));
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),self.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  jdbc.sql("delete from tree_access where tree_id=:t and user_id=:u").param("t",shared.getId()).param("u",member.getId()).update();
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),other.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"revoked\"}")).andExpect(status().isForbidden());
  grant(shared,member,"EDITOR");jdbc.sql("update members set deleted=true where id=:m").param("m",other.getId()).update();
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),other.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"deleted\"}")).andExpect(status().isNotFound());
 }
 @Test void bindingRequiresAdminValidMemberTargetReadAccessAndUniqueMember()throws Exception {
  var self=members.saveAndFlush(new Member(shared.getId(),"bindable",Gender.UNKNOWN));var session=login(admin);
  mvc.perform(put("/api/v1/admin/users/{id}/member",member.getId()).session(login(member)).with(csrf()).contentType("application/json").content("{\"memberId\":"+self.getId()+"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/users/{id}/member",member.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":"+self.getId()+"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/users/{id}/member",member.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":9223372036854775807}")).andExpect(status().isNotFound());
  mvc.perform(put("/api/v1/admin/users/{id}/member",member.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":-1}")).andExpect(status().isBadRequest());
  grant(shared,member,"EDITOR");bind(member,self,session);
  mvc.perform(put("/api/v1/admin/users/{id}/member",owner.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":"+self.getId()+"}")).andExpect(status().isConflict());
  assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.sql("update users set member_id=:m where id=:u").param("m",self.getId()).param("u",owner.getId()).update());
  mvc.perform(put("/api/v1/admin/users/{id}/member",member.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":null}")).andExpect(status().isOk()).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("memberTreeId").value(org.hamcrest.Matchers.nullValue()));
  bind(owner,self,session);
  var removed=members.saveAndFlush(new Member(shared.getId(),"deleted-member",Gender.UNKNOWN));removed.setDeleted(true);members.flush();
  mvc.perform(put("/api/v1/admin/users/{id}/member",member.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":"+removed.getId()+"}")).andExpect(status().isNotFound());
 }
 @Test void nullableIdentityFieldsAndAdminBindingAreSupported()throws Exception {
  var result=mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username",admin.getUsername(),"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
  var identity=json.readTree(result.getResponse().getContentAsString());assertTrue(identity.has("memberId"));assertTrue(identity.get("memberId").isNull());assertTrue(identity.has("memberTreeId"));assertTrue(identity.get("memberTreeId").isNull());
  var session=(MockHttpSession)result.getRequest().getSession(false);var self=members.saveAndFlush(new Member(secret.getId(),"admin-person",Gender.UNKNOWN));bind(admin,self,session);
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("memberId").value(self.getId().intValue()));
  mvc.perform(put("/api/v1/admin/users/{id}/member",admin.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":null}")).andExpect(status().isOk());
 }
 @Test void assignmentAndDirectRevocationClearBindingAndOldSessionImmediatelyLosesEdit()throws Exception {
  grant(shared,member,"EDITOR");var self=members.saveAndFlush(new Member(shared.getId(),"self",Gender.UNKNOWN));var session=login(admin);var old=login(member);bind(member,self,session);
  mvc.perform(put("/api/v1/admin/users/{id}/trees",member.getId()).session(session).with(csrf()).contentType("application/json").content(assignment(owned.getId()))).andExpect(status().isOk()).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("memberTreeId").value(org.hamcrest.Matchers.nullValue()));
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue()));
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",shared.getId(),self.getId()).session(old).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  grant(shared,member,"OWNER");bind(member,self,session);
  mvc.perform(delete("/api/v1/trees/{tree}/access/{user}",shared.getId(),member.getId()).session(session).with(csrf())).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue()));
  mvc.perform(get("/api/v1/trees/{tree}/members",shared.getId()).session(old)).andExpect(status().isForbidden());
 }
 @Test void deletedMemberAndDeletedAccountReleaseBinding()throws Exception {
  var self=members.saveAndFlush(new Member(owned.getId(),"to-delete",Gender.UNKNOWN));var session=login(admin);var old=login(member);bind(member,self,session);
  mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",owned.getId(),self.getId()).session(session).with(csrf())).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("memberTreeId").value(org.hamcrest.Matchers.nullValue()));
  var retained=members.saveAndFlush(new Member(owned.getId(),"retained",Gender.UNKNOWN));bind(member,retained,session);
  mvc.perform(delete("/api/v1/admin/users/{id}",member.getId()).session(session).with(csrf())).andExpect(status().isNoContent());
  assertNull(users.findById(member.getId()).orElseThrow().getMemberId());bind(admin,retained,session);
 }
 @Test void editorOwnerAndBoundSelfCannotManageTreesRelationsOrOtherWrites()throws Exception {
  var self=members.saveAndFlush(new Member(shared.getId(),"self",Gender.UNKNOWN));var other=members.saveAndFlush(new Member(shared.getId(),"other",Gender.UNKNOWN));
  jdbc.sql("insert into relationships(tree_id,from_member_id,to_member_id,relation_type,version) values(:t,:f,:x,'SIBLING',0)").param("t",shared.getId()).param("f",self.getId()).param("x",other.getId()).update();
  var storedRelation=jdbc.sql("select id from relationships where tree_id=:t").param("t",shared.getId()).query(Long.class).single();
  grant(shared,member,"EDITOR");var session=login(member);bind(member,self,login(admin));
  for(String role:List.of("EDITOR","OWNER")){
   jdbc.sql("update tree_access set access_role=:r where tree_id=:t and user_id=:u").param("r",role).param("t",shared.getId()).param("u",member.getId()).update();
   mvc.perform(post("/api/v1/trees/{tree}/members",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",shared.getId(),self.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/relationships",shared.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("fromMemberId",self.getId(),"toMemberId",other.getId(),"type","PARENT")))).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/trees/{tree}/relationships/{id}",shared.getId(),storedRelation).session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/trees/{tree}/members/{id}/sensitive",shared.getId(),self.getId()).session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/custom-fields",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"key\":\"test\",\"label\":\"test\",\"type\":\"TEXT\"}")).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/trees/{tree}/custom-fields/1/members/{id}",shared.getId(),self.getId()).session(session).with(csrf()).contentType("application/json").content("{\"value\":\"bad\"}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/stories",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"title\":\"denied\",\"content\":\"denied\"}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/suggestions",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"targetType\":\"MEMBER\"}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/backups",shared.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(multipart("/api/v1/trees/{tree}/media",shared.getId()).file("file",new byte[]{1}).session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/trees/{tree}/access",shared.getId()).session(session).with(csrf()).contentType("application/json").content("{\"username\":\"account-owner\",\"role\":\"OWNER\"}")).andExpect(status().isForbidden());
  }
 }
 @Test void onlyAdminCanCreateAndUpdateTreeIncludingForActualOwner()throws Exception {
  var session=login(member);
  mvc.perform(post("/api/v1/trees").session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/trees/{tree}",owned.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  var adminSession=login(admin);
  mvc.perform(post("/api/v1/trees").session(adminSession).with(csrf()).contentType("application/json").content("{\"name\":\"created\",\"description\":\"description\"}")).andExpect(status().isOk()).andExpect(jsonPath("ownerId").value(admin.getId().intValue()));
  mvc.perform(put("/api/v1/trees/{tree}",owned.getId()).session(adminSession).with(csrf()).contentType("application/json").content("{\"name\":\"updated\",\"description\":\"new description\",\"ownerId\":0,\"id\":0}"))
   .andExpect(status().isOk()).andExpect(jsonPath("id").value(owned.getId().intValue())).andExpect(jsonPath("ownerId").value(member.getId().intValue())).andExpect(jsonPath("name").value("updated")).andExpect(jsonPath("description").value("new description"));
 }
 @Test void familyAdminCreationAssignmentAndBindingUseMemberScopeRules()throws Exception {
  var session=login(admin);
  var result=mvc.perform(post("/api/v1/admin/users").session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("username","new-family-admin","password",PASSWORD,"role","FAMILY_ADMIN","treeIds",List.of(shared.getId(),shared.getId())))))
   .andExpect(status().isOk()).andExpect(jsonPath("role").value("FAMILY_ADMIN")).andExpect(jsonPath("assignedTreeIds").value(org.hamcrest.Matchers.contains(shared.getId().intValue()))).andReturn();
  var user=users.findById(json.readTree(result.getResponse().getContentAsString()).get("id").asLong()).orElseThrow();
  assertEquals("EDITOR",jdbc.sql("select access_role from tree_access where user_id=:u").param("u",user.getId()).query(String.class).single());
  var person=members.saveAndFlush(new Member(shared.getId(),"family-admin-binding",Gender.UNKNOWN));
  var hidden=members.saveAndFlush(new Member(secret.getId(),"hidden-binding",Gender.UNKNOWN));
  bind(user,person,session);var old=login(user);
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("role").value("FAMILY_ADMIN")).andExpect(jsonPath("memberId").value(person.getId().intValue()));
  mvc.perform(put("/api/v1/admin/users/{id}/member",user.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":"+hidden.getId()+"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/users/{id}/trees",user.getId()).session(session).with(csrf()).contentType("application/json").content(assignment(secret.getId()))).andExpect(status().isOk()).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue()));
  mvc.perform(get("/api/v1/trees/{tree}/members",shared.getId()).session(old)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/trees/{tree}/members",shared.getId()).session(old).with(csrf()).contentType("application/json").content("{\"name\":\"revoked\"}")).andExpect(status().isForbidden());
  bind(user,hidden,session);
  mvc.perform(get("/api/v1/trees").session(old)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(secret.getId().intValue()));
  mvc.perform(delete("/api/v1/trees/{tree}/access/{user}",secret.getId(),user.getId()).session(session).with(csrf())).andExpect(status().isOk());
  mvc.perform(get("/api/v1/auth/me").session(old)).andExpect(jsonPath("memberId").value(org.hamcrest.Matchers.nullValue()));
  mvc.perform(get("/api/v1/trees/{tree}/members",secret.getId()).session(old)).andExpect(status().isForbidden());
 }
 @Test void familyAdminCanManagePersonnelInOwnedAndViewerAssignedTreesWithoutBinding()throws Exception {
  member.setRole(Role.FAMILY_ADMIN);users.flush();grant(shared,member,"VIEWER");var session=login(member);
  mvc.perform(get("/api/v1/trees").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[?(@.name=='secret-tree')]").isEmpty());
  for(var tree:List.of(owned,shared)){
   var created=mvc.perform(post("/api/v1/trees/{tree}/members",tree.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"created-by-family-admin\",\"gender\":\"FEMALE\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("treeId").value(tree.getId().intValue())).andReturn();
   long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
   var avatar=new Media();avatar.setTreeId(tree.getId());avatar.setUploaderId(member.getId());avatar.setStoredName("family-avatar-"+tree.getId());avatar=media.saveAndFlush(avatar);
   mvc.perform(put("/api/v1/trees/{tree}/members/{id}",tree.getId(),id).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("name","updated-by-family-admin","avatarMediaId",avatar.getId()))))
    .andExpect(status().isOk()).andExpect(jsonPath("name").value("updated-by-family-admin")).andExpect(jsonPath("avatarMediaId").value(avatar.getId().intValue()));
   mvc.perform(put("/api/v1/trees/{tree}/members/{id}/sensitive",tree.getId(),id).session(session).with(csrf()).contentType("application/json").content("{\"privateNote\":\"scope-only\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("sensitiveJson").doesNotExist());
   mvc.perform(get("/api/v1/trees/{tree}/members/{id}/sensitive",tree.getId(),id).session(session)).andExpect(status().isOk()).andExpect(jsonPath("privateNote").value("scope-only"));
   mvc.perform(get("/api/v1/trees/{tree}/members/{id}",tree.getId(),id).session(session)).andExpect(status().isOk()).andExpect(jsonPath("sensitiveJson").doesNotExist());
   var person=members.findById(id).orElseThrow();bind(member,person,login(admin));
   mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",tree.getId(),id).session(session).with(csrf())).andExpect(status().isOk());
   assertTrue(members.findById(id).orElseThrow().isDeleted());assertNull(users.findById(member.getId()).orElseThrow().getMemberId());
   mvc.perform(get("/api/v1/trees/{tree}/members/{id}",tree.getId(),id).session(session)).andExpect(status().isNotFound());
  }
 }
 @Test void familyAdminCannotReadOrManageForeignPersonnelOrSmuggleIds()throws Exception {
  member.setRole(Role.FAMILY_ADMIN);users.flush();var session=login(member);
  var local=members.saveAndFlush(new Member(owned.getId(),"local",Gender.UNKNOWN));
  var hidden=members.saveAndFlush(new Member(secret.getId(),"foreign-private",Gender.UNKNOWN));
  var asset=new Media();asset.setTreeId(secret.getId());asset.setUploaderId(owner.getId());asset.setStoredName("foreign-avatar");asset=media.saveAndFlush(asset);
  for(String suffix:List.of("/members","/members/"+hidden.getId(),"/members/"+hidden.getId()+"/sensitive","/relationships","/statistics","/stories","/events"))
   mvc.perform(get("/api/v1/trees/"+secret.getId()+suffix).session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/trees/{tree}/members",secret.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",secret.getId(),hidden.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",secret.getId(),hidden.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}/sensitive",secret.getId(),hidden.getId()).session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",owned.getId(),hidden.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"wrong-tree\"}")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",owned.getId(),hidden.getId()).session(session).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(get("/api/v1/trees/{tree}/members/{id}/sensitive",owned.getId(),hidden.getId()).session(session)).andExpect(status().isNotFound());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}/sensitive",owned.getId(),hidden.getId()).session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isNotFound());
  var badAvatar=json.writeValueAsString(Map.of("name","foreign-avatar","avatarMediaId",asset.getId()));
  mvc.perform(post("/api/v1/trees/{tree}/members",owned.getId()).session(session).with(csrf()).contentType("application/json").content(badAvatar)).andExpect(status().isNotFound());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",owned.getId(),local.getId()).session(session).with(csrf()).contentType("application/json").content(badAvatar)).andExpect(status().isNotFound());
  assertEquals("local",members.findById(local.getId()).orElseThrow().getName());
 }
 @Test void familyAdminMediaUploadAndDownloadRemainTreeIsolated()throws Exception {
  member.setRole(Role.FAMILY_ADMIN);users.flush();grant(shared,member,"VIEWER");var session=login(member);
  var file=new org.springframework.mock.web.MockMultipartFile("file","person.txt","text/plain","person-media".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  for(var tree:List.of(owned,shared)){
   var result=mvc.perform(multipart("/api/v1/trees/{tree}/media",tree.getId()).file(file).session(session).with(csrf())).andExpect(status().isOk())
    .andExpect(jsonPath("treeId").value(tree.getId().intValue())).andExpect(jsonPath("uploaderId").value(member.getId().intValue())).andReturn();
   var uploaded=json.readTree(result.getResponse().getContentAsString());long id=uploaded.get("id").asLong();
   try{
    mvc.perform(get("/api/v1/trees/{tree}/media/{id}",tree.getId(),id).session(session)).andExpect(status().isOk()).andExpect(content().bytes(file.getBytes()));
    var other=tree.getId().equals(owned.getId())?shared:owned;
    mvc.perform(get("/api/v1/trees/{tree}/media/{id}",other.getId(),id).session(session)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/trees/{tree}/media/{id}",secret.getId(),id).session(session)).andExpect(status().isForbidden());
   }finally{java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(properties.storageRoot()).resolve(uploaded.get("storedName").asText()));}
  }
  mvc.perform(multipart("/api/v1/trees/{tree}/media",secret.getId()).file(file).session(session).with(csrf())).andExpect(status().isForbidden());
  var hidden=new Media();hidden.setTreeId(secret.getId());hidden.setUploaderId(owner.getId());hidden.setStoredName("private-download");hidden=media.saveAndFlush(hidden);
  mvc.perform(get("/api/v1/trees/{tree}/media/{id}",secret.getId(),hidden.getId()).session(session)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/trees/{tree}/media/{id}",owned.getId(),hidden.getId()).session(session)).andExpect(status().isNotFound());
 }
 @Test void familyAdminCannotManageSystemUsersEvenWithForgedAdminAuthority()throws Exception {
  member.setRole(Role.FAMILY_ADMIN);users.flush();var session=login(member);
  mvc.perform(get("/api/v1/admin/users").session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/admin/users").session(session).with(csrf()).contentType("application/json").content("{\"username\":\"denied\",\"password\":\"test-password-123\",\"role\":\"ADMIN\"}")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/admin/users/{id}",owner.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/users/{id}/enabled",owner.getId()).param("value","false").session(session).with(csrf())).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/users/{id}/trees",owner.getId()).session(session).with(csrf()).contentType("application/json").content(assignment())).andExpect(status().isForbidden());
  mvc.perform(put("/api/v1/admin/users/{id}/member",owner.getId()).session(session).with(csrf()).contentType("application/json").content("{\"memberId\":null}")).andExpect(status().isForbidden());
  var forged=UsernamePasswordAuthenticationToken.authenticated(member.getUsername(),null,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
  assertThrows(AccessDeniedException.class,()->adminService.list(forged));
  assertThrows(AccessDeniedException.class,()->adminService.create("forged-family-admin",PASSWORD,Role.ADMIN,List.of(),forged));
  assertThrows(AccessDeniedException.class,()->adminService.assign(owner.getId(),List.of(),forged));
  assertThrows(AccessDeniedException.class,()->adminService.bindMember(owner.getId(),null,forged));
  assertThrows(AccessDeniedException.class,()->adminService.enabled(owner.getId(),false,forged));
  assertThrows(AccessDeniedException.class,()->adminService.delete(owner.getId(),forged));
  assertThrows(AccessDeniedException.class,()->access.requireEdit(owned.getId(),forged));
  assertThrows(AccessDeniedException.class,()->access.requireOwner(owned.getId(),forged));
 }
 @Test void familyAdminCannotChangeMetadataRelationsAccessOrWorkflowsEvenAsOwner()throws Exception {
  member.setRole(Role.FAMILY_ADMIN);users.flush();grant(shared,member,"OWNER");var session=login(member);
  mvc.perform(post("/api/v1/trees").session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  for(var tree:List.of(owned,shared)){
   var first=members.saveAndFlush(new Member(tree.getId(),"first",Gender.UNKNOWN));var second=members.saveAndFlush(new Member(tree.getId(),"second",Gender.UNKNOWN));
   var relation=family.addRelation(tree.getId(),first.getId(),second.getId(),RelationType.SIBLING,auth(admin));
   mvc.perform(put("/api/v1/trees/{tree}",tree.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/relationships",tree.getId()).session(session).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("fromMemberId",first.getId(),"toMemberId",second.getId(),"type","PARENT")))).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/trees/{tree}/relationships/{id}",tree.getId(),relation.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",tree.getId(),first.getId()).session(session).with(csrf())).andExpect(status().isConflict());
   mvc.perform(get("/api/v1/trees/{tree}/access",tree.getId()).session(session)).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/trees/{tree}/access",tree.getId()).session(session).with(csrf()).contentType("application/json").content("{\"username\":\"account-owner\",\"role\":\"OWNER\"}")).andExpect(status().isForbidden());
   mvc.perform(delete("/api/v1/trees/{tree}/access/{user}",tree.getId(),owner.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
   for(String suffix:List.of("/stories","/events","/suggestions"))
    mvc.perform(post("/api/v1/trees/"+tree.getId()+suffix).session(session).with(csrf()).contentType("application/json").content("{\"title\":\"denied\",\"targetType\":\"MEMBER\"}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/suggestions/1/review",tree.getId()).session(session).with(csrf()).contentType("application/json").content("{\"approve\":true}")).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/backups",tree.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/exports",tree.getId()).param("format","JSON").session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(multipart("/api/v1/trees/{tree}/imports",tree.getId()).file("file","{}".getBytes(java.nio.charset.StandardCharsets.UTF_8)).param("format","JSON").session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(post("/api/v1/trees/{tree}/ocr",tree.getId()).param("mediaId","1").session(session).with(csrf())).andExpect(status().isForbidden());
   mvc.perform(put("/api/v1/trees/{tree}/ocr/1",tree.getId()).session(session).with(csrf()).contentType("application/json").content("{}")).andExpect(status().isForbidden());
  }
 }
 @Test void familyAdminOldSessionRoleChangesKeepMemberSelfOnlyAndAdminGlobalScope()throws Exception {
  var self=members.saveAndFlush(new Member(owned.getId(),"self",Gender.UNKNOWN));var other=members.saveAndFlush(new Member(owned.getId(),"other",Gender.UNKNOWN));bind(member,self,login(admin));var session=login(member);
  member.setRole(Role.FAMILY_ADMIN);users.flush();
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("role").value("FAMILY_ADMIN"));
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",owned.getId(),other.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"scope-edit\"}")).andExpect(status().isOk());
  member.setRole(Role.MEMBER);users.flush();
  mvc.perform(get("/api/v1/auth/me").session(session)).andExpect(jsonPath("role").value("MEMBER"));
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",owned.getId(),self.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"self-edit\"}")).andExpect(status().isOk());
  mvc.perform(put("/api/v1/trees/{tree}/members/{id}",owned.getId(),other.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/trees/{tree}/members/{id}/sensitive",owned.getId(),self.getId()).session(session)).andExpect(status().isForbidden());
  mvc.perform(post("/api/v1/trees/{tree}/members",owned.getId()).session(session).with(csrf()).contentType("application/json").content("{\"name\":\"denied\"}")).andExpect(status().isForbidden());
  mvc.perform(delete("/api/v1/trees/{tree}/members/{id}",owned.getId(),self.getId()).session(session).with(csrf())).andExpect(status().isForbidden());
  var oldAdmin=login(admin);admin.setRole(Role.FAMILY_ADMIN);users.flush();
  mvc.perform(get("/api/v1/auth/me").session(oldAdmin)).andExpect(jsonPath("role").value("FAMILY_ADMIN"));
  mvc.perform(get("/api/v1/admin/users").session(oldAdmin)).andExpect(status().isForbidden());
  mvc.perform(get("/api/v1/trees").session(oldAdmin)).andExpect(jsonPath("$").isEmpty());
  mvc.perform(get("/api/v1/trees/{tree}/members",secret.getId()).session(oldAdmin)).andExpect(status().isForbidden());
 }
 @Test void bootstrapNeverRecreatesOrRestoresDeletedAdministrator(){
  var tombstone=users.saveAndFlush(new User("bootstrap-tombstone",HASH,Role.ADMIN));tombstone.setDeleted(true);tombstone.setEnabled(false);users.flush();var before=users.count();
  var props=new AppProperties("unused",1,null,new AppProperties.BootstrapAdmin(tombstone.getUsername(),PASSWORD),null);
  new BootstrapAdmin(props,users,encoder).run(new DefaultApplicationArguments(new String[0]));
  assertEquals(before,users.count());var after=users.findByUsername(tombstone.getUsername()).orElseThrow();assertEquals(tombstone.getId(),after.getId());assertTrue(after.isDeleted());assertFalse(after.isEnabled());assertEquals(HASH,after.getPasswordHash());
 }
}
