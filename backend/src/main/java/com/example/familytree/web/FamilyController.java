package com.example.familytree.web;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.service.FamilyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@RestController @RequestMapping("/api/v1/trees")
public class FamilyController {
 private final FamilyService service; public FamilyController(FamilyService s){service=s;}
 record TreeInput(@NotBlank @Size(max=200) String name,@Size(max=5000) String description){}
 record MemberInput(@NotBlank @Size(max=160) String name,Gender gender,@Size(max=10) String birthDate,DatePrecision birthPrecision,@Size(max=255) String birthPlace,@Size(max=10) String deathDate,DatePrecision deathPrecision,@Size(max=255) String deathPlace,@Size(max=160) String occupation,@DecimalMin("0") @DecimalMax("300") BigDecimal heightCm,@Size(max=500) String address,@Size(max=10000) String notes,Long avatarMediaId) { Member toEntity(){var m=new Member(null,name,gender==null?Gender.UNKNOWN:gender);m.setBirthDate(birthDate);m.setBirthPrecision(birthPrecision);m.setBirthPlace(birthPlace);m.setDeathDate(deathDate);m.setDeathPrecision(deathPrecision);m.setDeathPlace(deathPlace);m.setOccupation(occupation);m.setHeightCm(heightCm);m.setAddress(address);m.setNotes(notes);m.setAvatarMediaId(avatarMediaId);return m;} }
 record RelationInput(@NotNull Long fromMemberId,@NotNull Long toMemberId,@NotNull RelationType type){}
 @PostMapping public FamilyTree create(@Valid @RequestBody TreeInput x,Authentication a){return service.createTree(x.name,x.description,a);}
 @PutMapping("/{tree}") public FamilyTree updateTree(@PathVariable Long tree,@Valid @RequestBody TreeInput x,Authentication a){return service.updateTree(tree,x.name,x.description,a);}
 @GetMapping public List<FamilyTree> trees(Authentication a){return service.trees(a);}
 @GetMapping("/{tree}/members") public List<Member> members(@PathVariable Long tree,Authentication a){return service.members(tree,a);}
 @GetMapping("/{tree}/relationships") public List<Relationship> relationships(@PathVariable Long tree,Authentication a){return service.relationships(tree,a);}
 @GetMapping("/{tree}/members/{id}") public Member get(@PathVariable Long tree,@PathVariable Long id,Authentication a){return service.get(tree,id,a);}
 @PostMapping("/{tree}/members") public Member create(@PathVariable Long tree,@Valid @RequestBody MemberInput x,Authentication a){return service.create(tree,x.toEntity(),a);}
 @PutMapping("/{tree}/members/{id}") public Member update(@PathVariable Long tree,@PathVariable Long id,@Valid @RequestBody MemberInput x,Authentication a){return service.update(tree,id,x.toEntity(),a);}
 @GetMapping("/{tree}/members/{id}/sensitive") public Map<String,Object> sensitiveGet(@PathVariable Long tree,@PathVariable Long id,Authentication a)throws Exception{var m=service.getSensitive(tree,id,a);return m.getSensitiveJson()==null?Map.of():new com.fasterxml.jackson.databind.ObjectMapper().readValue(m.getSensitiveJson(),Map.class);}
 @PutMapping("/{tree}/members/{id}/sensitive") public Member sensitive(@PathVariable Long tree,@PathVariable Long id,@RequestBody Map<String,Object> x,Authentication a)throws Exception{return service.updateSensitive(tree,id,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(x),a);}
 @DeleteMapping("/{tree}/members/{id}") public void delete(@PathVariable Long tree,@PathVariable Long id,Authentication a){service.delete(tree,id,a);}
 @PostMapping("/{tree}/relationships") public Relationship relation(@PathVariable Long tree,@Valid @RequestBody RelationInput x,Authentication a){return service.addRelation(tree,x.fromMemberId,x.toMemberId,x.type,a);}
 @DeleteMapping("/{tree}/relationships/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) public void removeRelation(@PathVariable Long tree,@PathVariable Long id,Authentication a){service.removeRelation(tree,id,a);}
 @GetMapping("/{tree}/members/{id}/ancestors") public List<Member> ancestors(@PathVariable Long tree,@PathVariable Long id,Authentication a){return service.ancestors(tree,id,a);}
 @GetMapping("/{tree}/members/{id}/descendants") public List<Member> descendants(@PathVariable Long tree,@PathVariable Long id,Authentication a){return service.descendants(tree,id,a);}
 @GetMapping("/{tree}/members/{id}/descendants/generations") public Map<Integer,List<Member>> generations(@PathVariable Long tree,@PathVariable Long id,Authentication a){return service.descendantsByGeneration(tree,id,a);}
 @GetMapping("/{tree}/relationship-path") public List<FamilyService.PathNode> path(@PathVariable Long tree,@RequestParam Long from,@RequestParam Long to,Authentication a){return service.path(tree,from,to,a);}
 @GetMapping("/{tree}/statistics") public Map<String,Object> stats(@PathVariable Long tree,Authentication a){return service.statistics(tree,a);}
}
