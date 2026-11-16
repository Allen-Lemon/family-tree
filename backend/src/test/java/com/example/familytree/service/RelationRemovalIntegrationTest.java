package com.example.familytree.service;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RelationRemovalIntegrationTest {
 @Autowired FamilyService service;
 @Autowired UserRepo users;
 @Autowired TreeRepo trees;
 @Autowired MemberRepo members;
 @Autowired RelationRepo relations;
 @org.junit.jupiter.api.BeforeEach void matchDatabaseDefaults(){jdbc.sql("alter table users alter column created_at set default current_timestamp").update();}

 @Test void administratorCanRemoveOneLinkAndQueriesKeepMembersButExcludeTheLink(){
  var owner=users.save(new User("relation-owner","unused",Role.ADMIN));
  var tree=trees.save(new FamilyTree("integration fixture","test only",owner.getId()));
  var parent=members.save(new Member(tree.getId(),"同名成员",Gender.MALE));
  var child=members.save(new Member(tree.getId(),"同名成员",Gender.FEMALE));
  var other=members.save(new Member(tree.getId(),"另一成员",Gender.UNKNOWN));
  var auth=new UsernamePasswordAuthenticationToken(owner.getUsername(),"unused");
  var removed=service.addRelation(tree.getId(),parent.getId(),child.getId(),RelationType.PARENT,auth);
  var kept=service.addRelation(tree.getId(),parent.getId(),other.getId(),RelationType.PARENT,auth);
  assertEquals(1,service.ancestors(tree.getId(),child.getId(),auth).size());
  service.removeRelation(tree.getId(),removed.getId(),auth);
  relations.flush();
  assertFalse(relations.existsById(removed.getId()));
  assertTrue(relations.existsById(kept.getId()));
  assertEquals(3,service.members(tree.getId(),auth).size());
  assertTrue(service.ancestors(tree.getId(),child.getId(),auth).isEmpty());
  assertTrue(service.path(tree.getId(),parent.getId(),child.getId(),auth).isEmpty());
  assertThrows(AccessService.NotFoundException.class,()->service.removeRelation(tree.getId(),removed.getId(),auth));
 }

 @Test void readOnlyViewerAndWrongTreeCannotRemoveLink(){
  var owner=users.save(new User("fixture-owner","unused",Role.ADMIN));
  var viewer=users.save(new User("fixture-viewer","unused",Role.MEMBER));
  var tree=trees.save(new FamilyTree("private fixture","",owner.getId()));
  var another=trees.save(new FamilyTree("other fixture","",owner.getId()));
  var parent=members.save(new Member(tree.getId(),"甲",Gender.UNKNOWN));
  var child=members.save(new Member(tree.getId(),"乙",Gender.UNKNOWN));
  var link=relations.saveAndFlush(new Relationship(tree.getId(),parent.getId(),child.getId(),RelationType.PARENT));
  // Use the actual access table to exercise authorization rather than a mocked AccessService.
  jdbc.sql("insert into tree_access(tree_id,user_id,access_role) values(:tree,:user,'VIEWER')").param("tree",tree.getId()).param("user",viewer.getId()).update();
  var viewerAuth=new UsernamePasswordAuthenticationToken(viewer.getUsername(),"unused");
  assertEquals(1,service.relationships(tree.getId(),viewerAuth).size());
  assertThrows(AccessDeniedException.class,()->service.removeRelation(tree.getId(),link.getId(),viewerAuth));
  var ownerAuth=new UsernamePasswordAuthenticationToken(owner.getUsername(),"unused");
  assertThrows(AccessService.NotFoundException.class,()->service.removeRelation(another.getId(),link.getId(),ownerAuth));
  assertTrue(relations.existsById(link.getId()));
 }
 @Test void spousesAndChildrenSynchronizeInEitherOrderWithoutCreatingMembers(){
  var owner=users.save(new User("sync-owner","unused",Role.ADMIN));
  var tree=trees.save(new FamilyTree("sync fixture","test only",owner.getId()));
  var first=members.save(new Member(tree.getId(),"甲",Gender.UNKNOWN));
  var second=members.save(new Member(tree.getId(),"乙",Gender.UNKNOWN));
  var child=members.save(new Member(tree.getId(),"孩子甲",Gender.UNKNOWN));
  var otherChild=members.save(new Member(tree.getId(),"孩子乙",Gender.UNKNOWN));
  var auth=new UsernamePasswordAuthenticationToken(owner.getUsername(),"unused");
  service.addRelation(tree.getId(),first.getId(),child.getId(),RelationType.PARENT,auth);
  service.addRelation(tree.getId(),second.getId(),otherChild.getId(),RelationType.PARENT,auth);
  service.addRelation(tree.getId(),second.getId(),first.getId(),RelationType.SPOUSE,auth);
  relations.flush();
  assertEquals(5,relations.findByTreeId(tree.getId()).size());
  assertEquals(2,service.ancestors(tree.getId(),child.getId(),auth).size());
  assertEquals(2,service.ancestors(tree.getId(),otherChild.getId(),auth).size());
  assertEquals(4,members.countByTreeIdAndDeletedFalse(tree.getId()));
  // After spouses already exist, a new parent edge immediately links both parents.
  var thirdChild=members.save(new Member(tree.getId(),"孩子丙",Gender.UNKNOWN));
  service.addRelation(tree.getId(),first.getId(),thirdChild.getId(),RelationType.PARENT,auth);
  relations.flush();
  assertEquals(7,relations.findByTreeId(tree.getId()).size());
  assertEquals(2,service.ancestors(tree.getId(),thirdChild.getId(),auth).size());
  assertEquals(5,members.countByTreeIdAndDeletedFalse(tree.getId()));
 }
 @Test void sharedChildrenAreNotDuplicatedAndUnrelatedFamiliesAreNotChanged(){
  var owner=users.save(new User("dedup-owner","unused",Role.ADMIN));
  var tree=trees.save(new FamilyTree("dedup fixture","test only",owner.getId()));
  var first=members.save(new Member(tree.getId(),"甲",Gender.UNKNOWN));
  var second=members.save(new Member(tree.getId(),"乙",Gender.UNKNOWN));
  var child=members.save(new Member(tree.getId(),"共同孩子",Gender.UNKNOWN));
  var unrelated=members.save(new Member(tree.getId(),"不相关成员",Gender.UNKNOWN));
  var auth=new UsernamePasswordAuthenticationToken(owner.getUsername(),"unused");
  service.addRelation(tree.getId(),first.getId(),child.getId(),RelationType.PARENT,auth);
  service.addRelation(tree.getId(),second.getId(),child.getId(),RelationType.PARENT,auth);
  service.addRelation(tree.getId(),first.getId(),second.getId(),RelationType.SPOUSE,auth);
  relations.flush();
  assertEquals(3,relations.findByTreeId(tree.getId()).size());
  assertTrue(relations.touching(tree.getId(),unrelated.getId()).isEmpty());
  assertEquals(4,members.countByTreeIdAndDeletedFalse(tree.getId()));
 }
 @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
}
