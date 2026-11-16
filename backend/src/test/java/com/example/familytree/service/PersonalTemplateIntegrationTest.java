package com.example.familytree.service;
import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PersonalTemplateIntegrationTest {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired UserRepo users;@Autowired TreeRepo trees;@Autowired MemberRepo members;
 User admin,manager,member;FamilyTree tree,secret;Member person;
 @BeforeEach void setup(){
  jdbc.sql("alter table users alter column created_at set default current_timestamp").update();
  jdbc.sql("create table if not exists custom_field_definitions (id bigint auto_increment primary key,tree_id bigint not null,field_key varchar(80) not null,label varchar(120) not null,field_type varchar(30) not null,sensitive boolean not null default false,enabled boolean not null default true,sort_order int not null default 0, unique(tree_id,field_key))").update();
  jdbc.sql("create table if not exists member_custom_values (member_id bigint not null,field_definition_id bigint not null,value_text text,primary key(member_id,field_definition_id))").update();
  admin=users.saveAndFlush(new User("template-root","unused",Role.ADMIN));manager=users.saveAndFlush(new User("template-local","unused",Role.FAMILY_ADMIN));member=users.saveAndFlush(new User("template-member","unused",Role.MEMBER));
  tree=trees.saveAndFlush(new FamilyTree("template scope","",admin.getId()));secret=trees.saveAndFlush(new FamilyTree("secret","",admin.getId()));person=members.saveAndFlush(new Member(tree.getId(),"fixture",Gender.UNKNOWN));
  for(var user:List.of(manager,member))jdbc.sql("insert into tree_access(tree_id,user_id,access_role) values(:t,:u,'VIEWER')").param("t",tree.getId()).param("u",user.getId()).update();
 }
 org.springframework.test.web.servlet.request.RequestPostProcessor account(User u){return user(new com.example.familytree.config.AccountPrincipal(u.getId(),org.springframework.security.core.userdetails.User.withUsername(u.getUsername()).password("unused").roles(u.getRole().name()).build()));}
 String path(){return "/api/v1/trees/"+tree.getId()+"/custom-fields";}
 long create(String key,String type,boolean sensitive)throws Exception {var result=mvc.perform(post(path()).with(account(manager)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("key",key,"label",key,"type",type,"sensitive",sensitive,"enabled",true,"sortOrder",0)))).andExpect(status().isOk()).andReturn();return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();}
 @Test void localManagerConfiguresOwnTemplateButCannotCrossTreesOrGrantOrdinaryWrites()throws Exception {
  long id=create("education","TEXT",false);
  mvc.perform(post(path()).with(account(manager)).with(csrf()).contentType("application/json").content("{\"key\":\"education\",\"label\":\"repeat\",\"type\":\"TEXT\"}")).andExpect(status().isConflict());
  mvc.perform(put(path()+"/"+id).with(account(manager)).with(csrf()).contentType("application/json").content("{\"label\":\"学历\",\"enabled\":true,\"sortOrder\":4}")).andExpect(status().isOk()).andExpect(jsonPath("label").value("学历"));
  mvc.perform(post("/api/v1/trees/"+secret.getId()+"/custom-fields").with(account(manager)).with(csrf()).contentType("application/json").content("{\"key\":\"x\",\"label\":\"x\",\"type\":\"TEXT\"}")).andExpect(status().isForbidden());
  mvc.perform(put(path()+"/"+id+"/members/"+person.getId()).with(account(member)).with(csrf()).contentType("application/json").content("{\"value\":\"x\"}")).andExpect(status().isForbidden());
 }
 @Test void typesAreValidatedAndInactiveValuesPreserved()throws Exception {
  long number=create("height_extra","NUMBER",false),date=create("graduated","DATE",false),bool=create("verified","BOOLEAN",false);
  for(var pair:List.of(Map.entry(number,"0"),Map.entry(date,"2000-02-29"),Map.entry(bool,"false")))mvc.perform(put(path()+"/"+pair.getKey()+"/members/"+person.getId()).with(account(manager)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("value",pair.getValue())))).andExpect(status().isOk());
  for(var pair:List.of(Map.entry(number,"NaN"),Map.entry(date,"2001-02-29"),Map.entry(bool,"yes")))mvc.perform(put(path()+"/"+pair.getKey()+"/members/"+person.getId()).with(account(manager)).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("value",pair.getValue())))).andExpect(status().isBadRequest());
  mvc.perform(put(path()+"/"+number).with(account(manager)).with(csrf()).contentType("application/json").content("{\"label\":\"停用数字\",\"enabled\":false,\"sortOrder\":2}")).andExpect(status().isOk());
  mvc.perform(put(path()+"/"+number+"/members/"+person.getId()).with(account(manager)).with(csrf()).contentType("application/json").content("{\"value\":\"1\"}")).andExpect(status().isConflict());
  assertEquals("0",jdbc.sql("select value_text from member_custom_values where member_id=:m and field_definition_id=:d").param("m",person.getId()).param("d",number).query(String.class).single());
 }
 @Test void sensitiveAndInactiveFieldsAreNotReturnedToOrdinaryMembers()throws Exception {
  create("public_field","TEXT",false);create("private_field","TEXT",true);long inactive=create("inactive","TEXT",false);
  mvc.perform(put(path()+"/"+inactive).with(account(manager)).with(csrf()).contentType("application/json").content("{\"label\":\"inactive\",\"enabled\":false,\"sortOrder\":0}")).andExpect(status().isOk());
  mvc.perform(get(path()).with(account(member))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].field_key").value("public_field"));
  mvc.perform(get(path()+"/members/"+person.getId()).with(account(member))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
  mvc.perform(get(path()).with(account(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
 }
}
