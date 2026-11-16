package com.example.familytree.service;

import com.example.familytree.config.AppProperties;
import com.example.familytree.domain.DomainModels.Media;
import com.example.familytree.repo.Repositories.MediaRepo;
import com.example.familytree.service.AccessService.BadRequestException;
import org.springframework.core.io.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Service
public class MediaService {
 private static final Set<String> ALLOWED=Set.of("image/jpeg","image/png","image/gif","application/pdf","text/plain","text/csv","application/json","application/zip");
 private final AppProperties p; private final AccessService access; private final MediaRepo repo; private final VirusScanner scanner;
 public MediaService(AppProperties p,AccessService a,MediaRepo r){this.p=p;access=a;repo=r;scanner=p.clamav()!=null&&p.clamav().enabled()?new ClamAvScanner(p.clamav().host(),p.clamav().port()):bytes->true;}
 public Media upload(Long tree,MultipartFile file,Authentication a)throws Exception{access.requirePersonnelEdit(tree,a);if(file.isEmpty()||file.getSize()>p.maxUploadBytes())throw new BadRequestException("文件为空或超过大小限制");var mime=Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");if(!ALLOWED.contains(mime))throw new BadRequestException("不允许的 MIME 类型");var bytes=file.getBytes();if(!scanner.clean(bytes))throw new BadRequestException("病毒扫描未通过");var root=Path.of(p.storageRoot()).toAbsolutePath().normalize();Files.createDirectories(root);var name=UUID.randomUUID().toString();var target=root.resolve(name).normalize();if(!target.startsWith(root))throw new BadRequestException("非法路径");Files.write(target,bytes,StandardOpenOption.CREATE_NEW);var m=new Media();m.setTreeId(tree);m.setUploaderId(access.current(a).getId());m.setOriginalName(Path.of(Optional.ofNullable(file.getOriginalFilename()).orElse("file")).getFileName().toString());m.setStoredName(name);m.setMimeType(mime);m.setSizeBytes(bytes.length);m.setSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));m.setScanStatus("CLEAN");return repo.save(m);}
 public Download download(Long tree,Long id,Authentication a){access.requireRead(tree,a);var m=repo.findByIdAndTreeId(id,tree).orElseThrow(()->new AccessService.NotFoundException("媒体不存在"));var root=Path.of(p.storageRoot()).toAbsolutePath().normalize();var f=root.resolve(m.getStoredName()).normalize();if(!f.startsWith(root)||!Files.isRegularFile(f))throw new AccessService.NotFoundException("媒体文件不存在");return new Download(m,new FileSystemResource(f));}
 public record Download(Media metadata,Resource resource){}
 interface VirusScanner{boolean clean(byte[] bytes)throws Exception;}
 static class ClamAvScanner implements VirusScanner{private final String host;private final int port;ClamAvScanner(String h,int p){host=h;port=p;}public boolean clean(byte[] b)throws Exception{try(var s=new Socket(host,port);var out=s.getOutputStream();var in=s.getInputStream()){out.write("zINSTREAM\0".getBytes());out.write(new byte[]{(byte)(b.length>>>24),(byte)(b.length>>>16),(byte)(b.length>>>8),(byte)b.length});out.write(b);out.write(new byte[4]);out.flush();return new String(in.readNBytes(512)).contains("OK");}}}
}
