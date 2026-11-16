package com.example.familytree.repo;

import com.example.familytree.domain.DomainModels.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public final class Repositories { private Repositories() {}
 public interface UserRepo extends JpaRepository<User,Long>{
  Optional<User> findByUsername(String username);
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  Optional<User> findByUsernameAndDeletedFalse(String username);
  List<User> findByDeletedFalseOrderByIdAsc();
  // Serialize account administration, including the last-available-admin invariant.
  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  List<User> findAllByOrderByIdAsc();
 }
 public interface TreeRepo extends JpaRepository<FamilyTree,Long>{List<FamilyTree> findByOwnerId(Long ownerId); @Query("select distinct t from FamilyTree t where t.ownerId=:user or t.id in (select a.treeId from TreeAccess a where a.userId=:user)") List<FamilyTree> accessible(@Param("user") Long userId);}
 public interface MemberRepo extends JpaRepository<Member,Long>{List<Member> findByTreeIdAndDeletedFalse(Long treeId); Optional<Member> findByIdAndTreeIdAndDeletedFalse(Long id,Long treeId); long countByTreeIdAndDeletedFalse(Long treeId);}
 public interface RelationRepo extends JpaRepository<Relationship,Long>{
  Optional<Relationship> findByIdAndTreeId(Long id,Long treeId);
  List<Relationship> findByTreeId(Long treeId); List<Relationship> findByTreeIdAndFromMemberId(Long treeId,Long memberId); List<Relationship> findByTreeIdAndToMemberId(Long treeId,Long memberId);
  boolean existsByTreeIdAndFromMemberIdAndToMemberIdAndRelationType(Long treeId,Long from,Long to,RelationType type);
  @Query("select r from Relationship r where r.treeId=:tree and (r.fromMemberId=:member or r.toMemberId=:member)") List<Relationship> touching(@Param("tree") Long tree,@Param("member") Long member);
 }
 public interface MediaRepo extends JpaRepository<Media,Long>{Optional<Media> findByIdAndTreeId(Long id,Long treeId);}
 public interface SuggestionRepo extends JpaRepository<Suggestion,Long>{List<Suggestion> findByTreeIdAndStatus(Long treeId,Status status);}
 public interface StoryRepo extends JpaRepository<Story,Long>{List<Story> findByTreeId(Long treeId);}
 public interface EventRepo extends JpaRepository<FamilyEvent,Long>{List<FamilyEvent> findByTreeId(Long treeId);}
 public interface NotificationRepo extends JpaRepository<Notification,Long>{List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);}
 public interface TransferJobRepo extends JpaRepository<TransferJob,Long>{}
 public interface BackupRepo extends JpaRepository<BackupRecord,Long>{List<BackupRecord> findByTreeIdOrderByCreatedAtDesc(Long treeId);}
 public interface OcrRepo extends JpaRepository<OcrJob,Long>{}
}
