package com.example.familytree.web;

import com.example.familytree.service.AccessService;
import com.example.familytree.domain.DomainModels.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController @RequestMapping("/api/v1/trees/{tree}/custom-fields")
public class CustomFieldController {
 private final AccessService access; private final JdbcClient jdbc;
 public CustomFieldController(AccessService a,JdbcClient j){access=a;jdbc=j;}
 record Definition(@NotBlank @Pattern(regexp="[a-zA-Z][a-zA-Z0-9_]{0,79}") String key,@NotBlank @Size(max=120) String label,@NotNull @Pattern(regexp="TEXT|NUMBER|DATE|BOOLEAN") String type,boolean sensitive,Boolean enabled,@Min(0) Integer sortOrder){}
 record Update(@NotBlank @Size(max=120) String label,@NotNull Boolean enabled,@NotNull @Min(0) Integer sortOrder){}
 private static final String COLUMNS="id,field_key,label,field_type,`sensitive`,enabled,sort_order";
 private boolean personnel(Long tree,Authentication a){var u=access.current(a);return u.getRole()==Role.ADMIN||(u.getRole()==Role.FAMILY_ADMIN&&access.canRead(tree,u.getId()));}
 @GetMapping List<Map<String,Object>> list(@PathVariable Long tree,Authentication a){
  access.requireRead(tree,a);
  var rows=jdbc.sql("select "+COLUMNS+" from custom_field_definitions where tree_id=:t order by sort_order,id").param("t",tree).query().listOfRows();
  return personnel(tree,a)?rows:rows.stream().filter(row->!flag(row,"sensitive")&&flag(row,"enabled")).toList();
 }
 @PostMapping Map<String,Object> create(@PathVariable Long tree,@Valid @RequestBody Definition d,Authentication a){
  access.requirePersonnelEdit(tree,a);
  try{jdbc.sql("insert into custom_field_definitions(tree_id,field_key,label,field_type,`sensitive`,enabled,sort_order) values(:t,:k,:l,:f,:s,:e,:o)").param("t",tree).param("k",d.key).param("l",d.label.trim()).param("f",d.type).param("s",d.sensitive).param("e",d.enabled==null||d.enabled).param("o",d.sortOrder==null?0:d.sortOrder).update();}
  catch(DuplicateKeyException e){throw new AccessService.ConflictException("此族谱已存在相同字段标识");}
  return jdbc.sql("select "+COLUMNS+" from custom_field_definitions where tree_id=:t and field_key=:k").param("t",tree).param("k",d.key).query().singleRow();
 }
 @PutMapping("/{definitionId}") Map<String,Object> update(@PathVariable Long tree,@PathVariable Long definitionId,@Valid @RequestBody Update d,Authentication a){
  access.requirePersonnelEdit(tree,a);
  definition(tree,definitionId);
  jdbc.sql("update custom_field_definitions set label=:l,enabled=:e,sort_order=:o where id=:d and tree_id=:t").param("l",d.label.trim()).param("e",d.enabled).param("o",d.sortOrder).param("d",definitionId).param("t",tree).update();
  return definition(tree,definitionId);
 }
 @PutMapping("/{definitionId}/members/{memberId}") void value(@PathVariable Long tree,@PathVariable Long definitionId,@PathVariable Long memberId,@RequestBody Map<String,String> body,Authentication a){
  access.requirePersonnelEdit(tree,a);var field=definition(tree,definitionId);requireMember(tree,memberId);
  if(!flag(field,"enabled"))throw new AccessService.ConflictException("字段已停用，历史资料保留但不可修改");
  String value=body.get("value");if(value==null)value="";
  validateValue(String.valueOf(field.get("field_type")),value);
  jdbc.sql("insert into member_custom_values(member_id,field_definition_id,value_text) values(:m,:d,:v) on duplicate key update value_text=:v").param("m",memberId).param("d",definitionId).param("v",value).update();
 }
 @GetMapping("/members/{memberId}") List<Map<String,Object>> values(@PathVariable Long tree,@PathVariable Long memberId,Authentication a){
  access.requireRead(tree,a);requireMember(tree,memberId);
  var rows=jdbc.sql("select d.id as definition_id,d.field_key,d.label,d.field_type,d.`sensitive`,d.enabled,d.sort_order,v.value_text from custom_field_definitions d left join member_custom_values v on v.field_definition_id=d.id and v.member_id=:m where d.tree_id=:t order by d.sort_order,d.id").param("m",memberId).param("t",tree).query().listOfRows();
  return personnel(tree,a)?rows:rows.stream().filter(row->!flag(row,"sensitive")&&flag(row,"enabled")).toList();
 }
 private Map<String,Object> definition(Long tree,Long id){var rows=jdbc.sql("select "+COLUMNS+" from custom_field_definitions where id=:d and tree_id=:t").param("d",id).param("t",tree).query().listOfRows();if(rows.isEmpty())throw new AccessService.NotFoundException("字段不存在");return rows.getFirst();}
 private void requireMember(Long tree,Long member){if(jdbc.sql("select count(*) from members where id=:m and tree_id=:t and deleted=false").param("m",member).param("t",tree).query(Long.class).single()==0)throw new AccessService.NotFoundException("成员不存在");}
 private boolean flag(Map<String,Object> row,String key){var value=row.get(key);return Boolean.TRUE.equals(value)||(value instanceof Number n&&n.intValue()!=0);}
 private void validateValue(String type,String value){
  if(value.length()>10000)throw new AccessService.BadRequestException("字段内容最多10000字");
  if(value.isEmpty())return;
  try{switch(type){case "NUMBER"->{if(value.length()>100||!value.matches("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"))throw new IllegalArgumentException();var n=new BigDecimal(value);if(!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException();}case "DATE"->{if(!value.matches("\\d{4}-\\d{2}-\\d{2}"))throw new IllegalArgumentException();LocalDate.parse(value);}case "BOOLEAN"->{if(!value.equals("true")&&!value.equals("false"))throw new IllegalArgumentException();}default->{}}}
  catch(IllegalArgumentException|DateTimeParseException e){throw new AccessService.BadRequestException("字段值不符合"+type+"类型");}
 }
}
