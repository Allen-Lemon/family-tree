package com.example.familytree.service;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.repo.Repositories.*;
import com.example.familytree.service.AccessService.*;
import jakarta.transaction.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class FamilyService {
 private final AccessService access; private final TreeRepo trees; private final MemberRepo members; private final RelationRepo relations;
 public FamilyService(AccessService a,TreeRepo t,MemberRepo m,RelationRepo r){access=a;trees=t;members=m;relations=r;}
 public FamilyTree createTree(String name,String description,Authentication auth){var u=access.requireAdmin(auth);return trees.save(new FamilyTree(name,description,u.getId()));}
 @Transactional public FamilyTree updateTree(Long tree,String name,String description,Authentication auth){var t=access.requireEdit(tree,auth);t.setName(name);t.setDescription(description);return trees.save(t);}
 public List<FamilyTree> trees(Authentication auth){var u=access.current(auth);var result=u.getRole()==Role.ADMIN?trees.findAll():trees.accessible(u.getId());result.forEach(t->t.setAccessRole(access.treeRole(t,u)));return result;}
 public List<Member> members(Long tree,Authentication a){access.requireRead(tree,a);return members.findByTreeIdAndDeletedFalse(tree);}
 public List<Relationship> relationships(Long tree,Authentication a){access.requireRead(tree,a);return relations.findByTreeId(tree);}
 @Transactional public void removeRelation(Long tree,Long id,Authentication a){access.requireEdit(tree,a);var relation=relations.findByIdAndTreeId(id,tree).orElseThrow(()->new NotFoundException("关系不存在或已解除"));relations.delete(relation);}
 public Member get(Long tree,Long id,Authentication a){access.requireRead(tree,a);return requireMember(tree,id);}
 public Member create(Long tree,Member input,Authentication a){access.requirePersonnelEdit(tree,a);access.requireMedia(tree,input.getAvatarMediaId());input.setId(null);input.setTreeId(tree);input.setDeleted(false);input.setSensitiveJson(null);return members.save(input);}
 @Transactional public Member update(Long tree,Long id,Member patch,Authentication a){access.requireMemberEdit(tree,id,a);var role=access.current(a).getRole();var admin=role==Role.ADMIN||role==Role.FAMILY_ADMIN;if(admin)access.requireMedia(tree,patch.getAvatarMediaId());var m=requireMember(tree,id);m.setName(patch.getName());m.setGender(patch.getGender());m.setBirthDate(patch.getBirthDate());m.setBirthPrecision(patch.getBirthPrecision());m.setBirthPlace(patch.getBirthPlace());m.setDeathDate(patch.getDeathDate());m.setDeathPrecision(patch.getDeathPrecision());m.setDeathPlace(patch.getDeathPlace());m.setOccupation(patch.getOccupation());m.setHeightCm(patch.getHeightCm());m.setAddress(patch.getAddress());m.setNotes(patch.getNotes());if(admin)m.setAvatarMediaId(patch.getAvatarMediaId());return members.save(m);}
 public Member getSensitive(Long tree,Long id,Authentication a){access.requirePersonnelEdit(tree,a);return requireMember(tree,id);}
 public Member updateSensitive(Long tree,Long id,String json,Authentication a){access.requirePersonnelEdit(tree,a);var m=requireMember(tree,id);m.setSensitiveJson(json);return members.save(m);}
 @Transactional public void delete(Long tree,Long id,Authentication a){access.requirePersonnelEdit(tree,a);var m=requireMember(tree,id);var touching=relations.touching(tree,id);if(!touching.isEmpty())throw new ConflictException("成员仍有关联关系，请先解除关系后再删除");access.clearMemberBindings(id);m.setDeleted(true);members.save(m);}
 @Transactional public Relationship addRelation(Long tree,Long from,Long to,RelationType type,Authentication a){
  access.requireEdit(tree,a);
  requireMember(tree,from); requireMember(tree,to);
  if(from.equals(to))throw new BadRequestException("不能与自身建立关系");
  Long f=from,t=to;
  if(type!=RelationType.PARENT&&f>t){f=to;t=from;}
  var existing=relations.findByTreeId(tree);
  final Long source=f,target=t;
  if(existing.stream().anyMatch(r->r.getFromMemberId().equals(source)&&r.getToMemberId().equals(target)&&r.getRelationType()==type))throw new ConflictException("关系已存在");
  var primary=new Relationship(tree,f,t,type);
  var pending=new LinkedHashMap<ParentLink,Relationship>();
  if(type==RelationType.PARENT){
   pending.put(new ParentLink(from,to),primary);
   // Only explicitly stored spouses of this parent participate; do not recurse through other marriages.
   for(var r:existing)if(r.getRelationType()==RelationType.SPOUSE&&(r.getFromMemberId().equals(from)||r.getToMemberId().equals(from))){
    Long spouse=r.getFromMemberId().equals(from)?r.getToMemberId():r.getFromMemberId();
    pending.putIfAbsent(new ParentLink(spouse,to),new Relationship(tree,spouse,to,RelationType.PARENT));
   }
  }else if(type==RelationType.SPOUSE){
   var children=new LinkedHashSet<Long>();
   for(var r:existing)if(r.getRelationType()==RelationType.PARENT&&(r.getFromMemberId().equals(from)||r.getFromMemberId().equals(to)))children.add(r.getToMemberId());
   for(Long child:children)for(Long parent:List.of(from,to))pending.putIfAbsent(new ParentLink(parent,child),new Relationship(tree,parent,child,RelationType.PARENT));
  }
  var graph=new HashMap<Long,Set<Long>>();
  for(var r:existing)if(r.getRelationType()==RelationType.PARENT)graph.computeIfAbsent(r.getFromMemberId(),ignored->new HashSet<>()).add(r.getToMemberId());
  // Validate every proposed edge before persisting any relationship, so a conflict cannot leave half a family linked.
  var additions=new ArrayList<Relationship>();
  var checkedMembers=new HashSet<Long>(List.of(from,to));
  for(var entry:pending.entrySet()){
   var link=entry.getKey();
   if(checkedMembers.add(link.parent()))requireMember(tree,link.parent());
   if(checkedMembers.add(link.child()))requireMember(tree,link.child());
   if(link.parent().equals(link.child()))throw new ConflictException("自动同步会形成自身亲子关系，请先核对成员和配偶");
   if(graph.getOrDefault(link.parent(),Set.of()).contains(link.child()))continue;
   if(parentReachable(graph,link.child(),link.parent()))throw new ConflictException("亲子关系或配偶子女同步会形成环，请先核对现有关系");
   graph.computeIfAbsent(link.parent(),ignored->new HashSet<>()).add(link.child());
   additions.add(entry.getValue());
  }
  if(type!=RelationType.PARENT)relations.save(primary);
  for(var addition:additions)relations.save(addition);
  return primary;
 }
 private record ParentLink(Long parent,Long child){}
 private boolean parentReachable(Map<Long,Set<Long>> graph,Long from,Long target){
  var seen=new HashSet<Long>();var queue=new ArrayDeque<Long>();queue.add(from);
  while(!queue.isEmpty()){var current=queue.remove();if(current.equals(target))return true;if(seen.add(current))queue.addAll(graph.getOrDefault(current,Set.of()));}
  return false;
 }
 public List<Member> ancestors(Long tree,Long id,Authentication a){access.requireRead(tree,a);return walk(tree,id,true);}
 public List<Member> descendants(Long tree,Long id,Authentication a){access.requireRead(tree,a);return descendants(tree,id);}
 public Map<Integer,List<Member>> descendantsByGeneration(Long tree,Long id,Authentication a){access.requireRead(tree,a);requireMember(tree,id);var out=new LinkedHashMap<Integer,List<Member>>();var seen=new HashSet<Long>();var frontier=List.of(id);int generation=0;while(!frontier.isEmpty()){var next=new ArrayList<Long>();for(Long x:frontier)for(var r:relations.findByTreeIdAndFromMemberId(tree,x))if(r.getRelationType()==RelationType.PARENT&&seen.add(r.getToMemberId()))next.add(r.getToMemberId());if(!next.isEmpty())out.put(++generation,next.stream().map(x->requireMember(tree,x)).toList());frontier=next;}return out;}
 public List<PathNode> path(Long tree,Long from,Long to,Authentication a){access.requireRead(tree,a);requireMember(tree,from);requireMember(tree,to);record Step(Long id,List<PathNode> p){}var q=new ArrayDeque<Step>();q.add(new Step(from,new ArrayList<>()));var seen=new HashSet<Long>();seen.add(from);while(!q.isEmpty()){var s=q.remove();if(s.id.equals(to))return s.p;for(var e:neighbors(tree,s.id)){if(seen.add(e.memberId)){var p=new ArrayList<>(s.p);p.add(e);q.add(new Step(e.memberId,p));}}}return List.of();}
 public record PathNode(Long memberId,String relation){}
 public Map<String,Object> statistics(Long tree,Authentication a){access.requireRead(tree,a);var all=members.findByTreeIdAndDeletedFalse(tree);var byGender=new TreeMap<String,Long>();for(var m:all)byGender.merge(m.getGender().name(),1L,Long::sum);return Map.of("members",all.size(),"relationships",relations.findAll().stream().filter(r->r.getTreeId().equals(tree)).count(),"byGender",byGender);}
 private List<PathNode> neighbors(Long tree,Long id){var out=new ArrayList<PathNode>();for(var r:relations.touching(tree,id)){if(r.getRelationType()==RelationType.PARENT){if(r.getFromMemberId().equals(id))out.add(new PathNode(r.getToMemberId(),"CHILD"));else out.add(new PathNode(r.getFromMemberId(),"PARENT"));}else out.add(new PathNode(r.getFromMemberId().equals(id)?r.getToMemberId():r.getFromMemberId(),r.getRelationType().name()));}return out;}
 private List<Member> walk(Long tree,Long id,boolean up){requireMember(tree,id);var out=new ArrayList<Member>();var seen=new HashSet<Long>();var q=new ArrayDeque<Long>();q.add(id);while(!q.isEmpty()){Long x=q.remove();var rs=up?relations.findByTreeIdAndToMemberId(tree,x):relations.findByTreeIdAndFromMemberId(tree,x);for(var r:rs)if(r.getRelationType()==RelationType.PARENT){Long n=up?r.getFromMemberId():r.getToMemberId();if(seen.add(n)){out.add(requireMember(tree,n));q.add(n);}}}return out;}
 private List<Member> descendants(Long tree,Long id){return walk(tree,id,false);}
 private Member requireMember(Long tree,Long id){return members.findByIdAndTreeIdAndDeletedFalse(id,tree).orElseThrow(()->new NotFoundException("成员不存在"));}
}
