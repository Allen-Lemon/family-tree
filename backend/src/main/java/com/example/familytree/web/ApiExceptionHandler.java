package com.example.familytree.web;

import com.example.familytree.service.AccessService.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
 record ErrorBody(Instant timestamp,int status,String code,String message,String path,Map<String,String> fields){}
 @ExceptionHandler(NotFoundException.class) ResponseEntity<ErrorBody> notFound(Exception e,HttpServletRequest r){return body(404,"NOT_FOUND",e,r,Map.of());}
 @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class) ResponseEntity<ErrorBody> missingRoute(Exception e,HttpServletRequest r){return body(404,"NOT_FOUND",new NotFoundException("接口不存在"),r,Map.of());}
 @ExceptionHandler({ConflictException.class,OptimisticLockingFailureException.class}) ResponseEntity<ErrorBody> conflict(Exception e,HttpServletRequest r){return body(409,"CONFLICT",e,r,Map.of());}
 @ExceptionHandler(BadRequestException.class) ResponseEntity<ErrorBody> bad(Exception e,HttpServletRequest r){return body(400,"BAD_REQUEST",e,r,Map.of());}
 @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,org.springframework.web.bind.MissingServletRequestParameterException.class}) ResponseEntity<ErrorBody> missingUpload(Exception e,HttpServletRequest r){return body(400,"BAD_REQUEST",new BadRequestException("缺少必需请求参数"),r,Map.of());}
 @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class) ResponseEntity<ErrorBody> uploadTooLarge(Exception e,HttpServletRequest r){return body(413,"PAYLOAD_TOO_LARGE",new BadRequestException("上传文件过大"),r,Map.of());}
 @ExceptionHandler(org.springframework.security.core.AuthenticationException.class) ResponseEntity<ErrorBody> unauthorized(Exception e,HttpServletRequest r){org.springframework.security.core.context.SecurityContextHolder.clearContext();var session=r.getSession(false);if(session!=null)session.invalidate();return body(401,"UNAUTHORIZED",e,r,Map.of());}
 @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class) ResponseEntity<ErrorBody> malformed(Exception e,HttpServletRequest r){return body(400,"BAD_REQUEST",new BadRequestException("请求格式不正确"),r,Map.of());}
 @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ErrorBody> denied(Exception e,HttpServletRequest r){return body(403,"FORBIDDEN",e,r,Map.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException e,HttpServletRequest r){var f=new LinkedHashMap<String,String>();e.getBindingResult().getFieldErrors().forEach(x->f.put(x.getField(),x.getDefaultMessage()));return body(400,"VALIDATION_ERROR",e,r,f);}
 @ExceptionHandler(com.example.familytree.service.ProfilePortraitStorage.PortraitPurgeException.class) ResponseEntity<ErrorBody> purgeIncomplete(Exception e,HttpServletRequest r){return body(503,"PRIVATE_PORTRAIT_PURGE_PENDING",e,r,Map.of());}
 @ExceptionHandler(Exception.class) ResponseEntity<ErrorBody> other(Exception e,HttpServletRequest r){return ResponseEntity.status(500).body(new ErrorBody(Instant.now(),500,"INTERNAL_ERROR","服务器内部错误",r.getRequestURI(),Map.of()));}
 private ResponseEntity<ErrorBody> body(int status,String code,Exception e,HttpServletRequest r,Map<String,String> f){return ResponseEntity.status(status).body(new ErrorBody(Instant.now(),status,code,e.getMessage(),r.getRequestURI(),f));}
}
