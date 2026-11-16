package com.example.familytree.web;

import com.example.familytree.domain.DomainModels.Role;
import com.example.familytree.service.UserAdministrationService;
import com.example.familytree.service.UserAdministrationService.UserDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/admin/users")
public class AdminController {
 private final UserAdministrationService service;
 public AdminController(UserAdministrationService service){this.service=service;}
 record NewUser(@NotBlank @Size(max=80) String username,@NotBlank @Size(min=10,max=100) String password,Role role,List<@NotNull @Positive Long> treeIds){}
 record Assignment(@NotNull List<@NotNull @Positive Long> treeIds){}
 record MemberBinding(@Positive Long memberId){}
 record RoleChange(@NotNull Role role){}
 @PutMapping("/{id}/role") UserDto role(@PathVariable Long id,@Valid @RequestBody RoleChange x,Authentication auth){return service.role(id,x.role(),auth);}
 @PutMapping("/{id}/member") UserDto bindMember(@PathVariable Long id,@Valid @RequestBody MemberBinding x,Authentication auth){return service.bindMember(id,x.memberId(),auth);}
 @GetMapping List<UserDto> list(Authentication auth){return service.list(auth);}
 @PostMapping UserDto create(@Valid @RequestBody NewUser x,Authentication auth){return service.create(x.username(),x.password(),x.role(),x.treeIds(),auth);}
 @PutMapping("/{id}/enabled") UserDto enabled(@PathVariable Long id,@RequestParam boolean value,Authentication auth){return service.enabled(id,value,auth);}
 @PutMapping("/{id}/trees") UserDto assign(@PathVariable Long id,@Valid @RequestBody Assignment x,Authentication auth){return service.assign(id,x.treeIds(),auth);}
 @DeleteMapping("/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) void delete(@PathVariable Long id,Authentication auth){service.delete(id,auth);}
}
