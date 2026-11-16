package com.example.familytree.service;

import org.springframework.stereotype.Component;

public interface OcrProvider {
 String name();
 Result recognize(byte[] content,String mimeType)throws Exception;
 record Result(String text,String draftMemberJson){}

 @Component
 class ManualOcrProvider implements OcrProvider {
  public String name(){return "manual";}
  public Result recognize(byte[] content,String mimeType){return new Result("", "{}");}
 }
}
