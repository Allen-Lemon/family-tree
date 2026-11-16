package com.example.familytree.domain;

import jakarta.persistence.*;
import lombok.*;
import com.example.familytree.config.SensitiveStringConverter;
import java.math.BigDecimal;
import java.time.Instant;

public final class DomainModels {
 private DomainModels() {}
 public enum Role { ADMIN, FAMILY_ADMIN, MEMBER }
 public enum AccessRole { OWNER, EDITOR, VIEWER }
 public enum Gender { MALE, FEMALE, OTHER, UNKNOWN }
 public enum DatePrecision { DAY, MONTH, YEAR, APPROXIMATE, UNKNOWN }
 public enum RelationType { PARENT, SPOUSE, SIBLING }
 public enum Status { PENDING, APPROVED, REJECTED, RUNNING, COMPLETED, FAILED }

 @Entity @Table(name="users") @Getter @Setter @NoArgsConstructor
 public static class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id;
  @Column(nullable=false,unique=true,length=80) String username;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="password_hash",nullable=false,length=100) String passwordHash;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Role role;
  @Column(nullable=false) boolean enabled=true;
  @Column(nullable=false) boolean deleted=false;
  @Column(name="member_id",unique=true) Long memberId;
  // Profile columns are written explicitly by ProfileService, never by account/member updates.
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="display_name",length=160,insertable=false,updatable=false) String displayName;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="birth_date",insertable=false,updatable=false) java.time.LocalDate profileBirthDate;
  @com.fasterxml.jackson.annotation.JsonIgnore @Enumerated(EnumType.STRING) @org.hibernate.annotations.ColumnDefault("'UNKNOWN'") @Column(name="gender",nullable=false,length=20,insertable=false,updatable=false) Gender profileGender;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="portrait_stored_name",length=100,insertable=false,updatable=false) String portraitStoredName;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="portrait_version",length=36,insertable=false,updatable=false) String portraitVersion;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="spouse_status",length=120,insertable=false,updatable=false) String spouseStatus;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="education",length=120,insertable=false,updatable=false) String education;
  @com.fasterxml.jackson.annotation.JsonIgnore @Column(name="biography",length=5000,insertable=false,updatable=false) String biography;
  @Column(name="created_at",nullable=false,insertable=false,updatable=false) Instant createdAt;
  public User(String username,String passwordHash,Role role){this.username=username;this.passwordHash=passwordHash;this.role=role;}
 }
 @Entity(name="FamilyTree") @Table(name="family_trees") @Getter @Setter @NoArgsConstructor
 public static class FamilyTree {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(nullable=false) String name; @Lob String description;
  @Column(name="owner_id",nullable=false) Long ownerId; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Version long version;
  @Transient AccessRole accessRole;
  public FamilyTree(String name,String description,Long ownerId){this.name=name;this.description=description;this.ownerId=ownerId;}
 }
 @Entity(name="TreeAccess") @Table(name="tree_access") @IdClass(TreeAccess.Key.class) @Getter @Setter @NoArgsConstructor
 public static class TreeAccess { @Id @Column(name="tree_id") Long treeId; @Id @Column(name="user_id") Long userId; @Enumerated(EnumType.STRING) @Column(name="access_role") AccessRole accessRole; @Data @NoArgsConstructor @AllArgsConstructor public static class Key implements java.io.Serializable { Long treeId; Long userId; } }
 @Entity @Table(name="members") @Getter @Setter @NoArgsConstructor
 public static class Member {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id",nullable=false) Long treeId; @Column(nullable=false,length=160) String name;
  @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) Gender gender=Gender.UNKNOWN;
  @Column(name="birth_date",length=10) String birthDate; @Enumerated(EnumType.STRING) @Column(name="birth_precision",length=20) DatePrecision birthPrecision;
  @Column(name="birth_place") String birthPlace; @Column(name="death_date",length=10) String deathDate; @Enumerated(EnumType.STRING) @Column(name="death_precision",length=20) DatePrecision deathPrecision;
  @Column(name="death_place") String deathPlace; String occupation; @Column(name="height_cm") BigDecimal heightCm; @Column(length=500) String address; @Lob String notes;
  @Column(name="avatar_media_id") Long avatarMediaId; @com.fasterxml.jackson.annotation.JsonIgnore @Convert(converter=SensitiveStringConverter.class) @Column(name="sensitive_json",columnDefinition="LONGTEXT") String sensitiveJson; @Column(nullable=false) boolean deleted=false;
  @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Column(name="updated_at",insertable=false) Instant updatedAt; @Version long version;
  public Member(Long treeId,String name,Gender gender){this.treeId=treeId;this.name=name;this.gender=gender;}
  @PreUpdate void touch(){updatedAt=Instant.now();}
 }
 @Entity(name="Relationship") @Table(name="relationships",uniqueConstraints=@UniqueConstraint(columnNames={"tree_id","from_member_id","to_member_id","relation_type"})) @Getter @Setter @NoArgsConstructor
 public static class Relationship {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id",nullable=false) Long treeId; @Column(name="from_member_id",nullable=false) Long fromMemberId;
  @Column(name="to_member_id",nullable=false) Long toMemberId; @Enumerated(EnumType.STRING) @Column(name="relation_type",nullable=false) RelationType relationType;
  @Column(name="start_date") String startDate; @Column(name="end_date") String endDate; @Column(length=1000) String notes; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Version long version;
  public Relationship(Long treeId,Long from,Long to,RelationType type){this.treeId=treeId;this.fromMemberId=from;this.toMemberId=to;this.relationType=type;}
 }
 @Entity @Table(name="media") @Getter @Setter @NoArgsConstructor
 public static class Media {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="uploader_id") Long uploaderId; @Column(name="original_name") String originalName;
  @Column(name="stored_name",unique=true) String storedName; @Column(name="mime_type") String mimeType; @Column(name="size_bytes") long sizeBytes; String sha256; @Column(name="scan_status") String scanStatus;
  @Column(name="created_at",insertable=false,updatable=false) Instant createdAt;
 }
 @Entity @Table(name="change_suggestions") @Getter @Setter @NoArgsConstructor
 public static class Suggestion {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="submitter_id") Long submitterId; @Column(name="target_type") String targetType;
  @Column(name="target_id") Long targetId; @Column(name="payload_json",columnDefinition="json") String payloadJson; @Enumerated(EnumType.STRING) Status status=Status.PENDING; @Column(name="reviewer_id") Long reviewerId;
  @Column(name="review_comment") String reviewComment; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Column(name="reviewed_at") Instant reviewedAt;
 }
 @Entity @Table(name="stories") @Getter @Setter @NoArgsConstructor
 public static class Story {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="member_id") Long memberId; @Column(name="author_id") Long authorId;
  String title; @Lob String content; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Column(name="updated_at",insertable=false) Instant updatedAt; @Version long version; @PreUpdate void touch(){updatedAt=Instant.now();}
 }
 @Entity @Table(name="family_events") @Getter @Setter @NoArgsConstructor
 public static class FamilyEvent {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="member_id") Long memberId; String title; @Column(name="event_type") String eventType;
  @Column(name="event_date") String eventDate; @Enumerated(EnumType.STRING) @Column(name="date_precision",length=20) DatePrecision datePrecision; String place; @Lob String description; @Column(name="remind_at") Instant remindAt; @Column(name="created_by") Long createdBy; @Version long version;
 }
 @Entity @Table(name="notifications") @Getter @Setter @NoArgsConstructor
 public static class Notification {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="user_id") Long userId; @Column(name="tree_id") Long treeId; String type; String message; @Column(name="read_at") Instant readAt; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt;
 }
 @Entity @Table(name="transfer_jobs") @Getter @Setter @NoArgsConstructor
 public static class TransferJob {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="user_id") Long userId; String direction; String format; @Enumerated(EnumType.STRING) Status status;
  @Column(name="source_media_id") Long sourceMediaId; @Column(name="result_path") String resultPath; @Lob @Column(name="error_message") String errorMessage; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Column(name="finished_at") Instant finishedAt;
 }
 @Entity @Table(name="backup_records") @Getter @Setter @NoArgsConstructor
 public static class BackupRecord {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="created_by") Long createdBy; @Column(name="stored_path") String storedPath; @Column(name="size_bytes") long sizeBytes; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt;
 }
 @Entity @Table(name="ocr_jobs") @Getter @Setter @NoArgsConstructor
 public static class OcrJob {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) Long id; @Column(name="tree_id") Long treeId; @Column(name="media_id") Long mediaId; @Column(name="requested_by") Long requestedBy; String provider; @Enumerated(EnumType.STRING) Status status;
  @Lob @Column(name="raw_text") String rawText; @Lob @Column(name="corrected_text") String correctedText; @Column(name="draft_member_json",columnDefinition="json") String draftMemberJson; @Column(name="created_at",insertable=false,updatable=false) Instant createdAt; @Column(name="finished_at") Instant finishedAt;
 }
}
