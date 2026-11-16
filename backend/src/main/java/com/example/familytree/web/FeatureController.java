package com.example.familytree.web;

import com.example.familytree.domain.DomainModels.*;
import com.example.familytree.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController @RequestMapping("/api/v1")
public class FeatureController {
 private final MediaService media; private final WorkflowService workflows; private final List<SseEmitter> emitters=new CopyOnWriteArrayList<>();
 public FeatureController(MediaService m,WorkflowService w){media=m;workflows=w;}
 @PostMapping("/trees/{tree}/media") Media upload(@PathVariable Long tree,@RequestPart MultipartFile file,Authentication a)throws Exception{return media.upload(tree,file,a);}
 @GetMapping("/trees/{tree}/media/{id}") ResponseEntity<Resource> download(@PathVariable Long tree,@PathVariable Long id,Authentication a){var d=media.download(tree,id,a);return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.metadata().getMimeType())).header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(d.metadata().getOriginalName(),java.nio.charset.StandardCharsets.UTF_8).build().toString()).body(d.resource());}
 record SuggestionInput(@NotBlank String targetType,Long targetId,Map<String,Object> payload){}
 @PostMapping("/trees/{tree}/suggestions") Suggestion suggest(@PathVariable Long tree,@Valid @RequestBody SuggestionInput x,Authentication a)throws Exception{return workflows.suggest(tree,x.targetType,x.targetId,x.payload==null?Map.of():x.payload,a);}
 @GetMapping("/trees/{tree}/suggestions/pending") List<Suggestion> pending(@PathVariable Long tree,Authentication a){return workflows.pending(tree,a);}
 record Review(boolean approve,String comment){}
 @PostMapping("/trees/{tree}/suggestions/{id}/review") Suggestion review(@PathVariable Long tree,@PathVariable Long id,@RequestBody Review x,Authentication a)throws Exception{return workflows.review(tree,id,x.approve,x.comment,a);}
 @PostMapping("/trees/{tree}/stories") Story story(@PathVariable Long tree,@RequestBody Story x,Authentication a){return workflows.saveStory(tree,x,a);}
 @GetMapping("/trees/{tree}/stories") List<Story> stories(@PathVariable Long tree,Authentication a){return workflows.stories(tree,a);}
 @PostMapping("/trees/{tree}/events") FamilyEvent event(@PathVariable Long tree,@RequestBody FamilyEvent x,Authentication a){return workflows.saveEvent(tree,x,a);}
 @GetMapping("/trees/{tree}/events") List<FamilyEvent> events(@PathVariable Long tree,Authentication a){return workflows.events(tree,a);}
 @GetMapping("/notifications") List<Notification> notifications(Authentication a){return workflows.notifications(a);}
 @PostMapping("/notifications/{id}/read") Notification read(@PathVariable Long id,Authentication a){return workflows.readNotification(id,a);}
 @PostMapping("/trees/{tree}/exports") TransferJob export(@PathVariable Long tree,@RequestParam String format,Authentication a)throws Exception{return workflows.exportTree(tree,format,a);}
 @PostMapping("/trees/{tree}/imports") TransferJob importData(@PathVariable Long tree,@RequestParam String format,@RequestPart MultipartFile file,Authentication a)throws Exception{return workflows.importData(tree,format,file.getBytes(),a);}
 @GetMapping("/trees/{tree}/exports/sync") ResponseEntity<byte[]> exportSync(@PathVariable Long tree,@RequestParam String format,Authentication a)throws Exception{workflows.stories(tree,a);var bytes=workflows.exportBytes(tree,format);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=family."+format.toLowerCase()).body(bytes);}
 @PostMapping("/trees/{tree}/backups") BackupRecord backup(@PathVariable Long tree,Authentication a)throws Exception{return workflows.backup(tree,a);}
 @GetMapping("/trees/{tree}/backups") List<BackupRecord> backups(@PathVariable Long tree,Authentication a){return workflows.backups(tree,a);}
 @GetMapping("/trees/{tree}/backups/{id}/download") ResponseEntity<Resource> backupDownload(@PathVariable Long tree,@PathVariable Long id,Authentication a){Path p=workflows.backupPath(tree,id,a);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=backup.json").body(new FileSystemResource(p));}
 @PostMapping("/trees/{tree}/ocr") OcrJob ocr(@PathVariable Long tree,@RequestParam Long mediaId,@RequestParam(required=false) String provider,Authentication a){return workflows.startOcr(tree,mediaId,provider,a);}
 record Correction(String correctedText,Map<String,Object> draftMember){}
 @PutMapping("/trees/{tree}/ocr/{id}") OcrJob correct(@PathVariable Long tree,@PathVariable Long id,@RequestBody Correction x,Authentication a)throws Exception{return workflows.correctOcr(tree,id,x.correctedText,x.draftMember==null?Map.of():x.draftMember,a);}
 @GetMapping("/trees/{tree}/audit") Map<String,Object> audit(@PathVariable Long tree,Authentication a){return workflows.audit(tree,a);}
 @GetMapping("/trees/{tree}/search") Map<String,Object> search(@PathVariable Long tree,@RequestParam String q,Authentication a){return workflows.search(tree,q,a);}
 @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE) SseEmitter stream(Authentication a){var e=new SseEmitter(0L);emitters.add(e);e.onCompletion(()->emitters.remove(e));try{e.send(SseEmitter.event().name("connected").data(Map.of("user",a.getName())));}catch(Exception ignored){}return e;}
}
