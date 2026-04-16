package com.pch.file.domain;

import com.pch.common.audit.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OwnerType ownerType;

    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false, length = 500)
    private String originalName;

    @Column(nullable = false, length = 500)
    private String storedKey;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false)
    private Long uploaderId;

    @Column(nullable = false)
    private boolean deleted = false;

    @Builder
    private Attachment(OwnerType ownerType, Long ownerId, String originalName,
                       String storedKey, String mimeType, Long fileSize, Long uploaderId) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.originalName = originalName;
        this.storedKey = storedKey;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
        this.uploaderId = uploaderId;
    }

    public static Attachment create(OwnerType ownerType, Long ownerId, String originalName,
                                     String storedKey, String mimeType, Long fileSize, Long uploaderId) {
        return Attachment.builder()
                .ownerType(ownerType)
                .ownerId(ownerId)
                .originalName(originalName)
                .storedKey(storedKey)
                .mimeType(mimeType)
                .fileSize(fileSize)
                .uploaderId(uploaderId)
                .build();
    }

    public void softDelete() {
        this.deleted = true;
    }
}
