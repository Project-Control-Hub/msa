package com.pch.file.repository;

import com.pch.file.domain.Attachment;
import com.pch.file.domain.OwnerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByOwnerTypeAndOwnerIdAndDeletedFalse(OwnerType ownerType, Long ownerId);

    List<Attachment> findByOwnerTypeAndOwnerIdInAndDeletedFalse(OwnerType ownerType, List<Long> ownerIds);

    @Modifying
    @Query("UPDATE Attachment a SET a.deleted = true WHERE a.ownerType = :ownerType AND a.ownerId = :ownerId AND a.deleted = false")
    int softDeleteByOwner(OwnerType ownerType, Long ownerId);

    @Query("SELECT a FROM Attachment a WHERE a.deleted = true AND a.updatedAt < :threshold")
    List<Attachment> findSoftDeletedBefore(LocalDateTime threshold);
}
