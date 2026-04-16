package com.pch.file.dto;

import com.pch.file.domain.Attachment;
import com.pch.file.domain.OwnerType;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        OwnerType ownerType,
        Long ownerId,
        String originalName,
        String mimeType,
        Long fileSize,
        Long uploaderId,
        LocalDateTime createdAt
) {
    public static AttachmentResponse from(Attachment a) {
        return new AttachmentResponse(
                a.getId(), a.getOwnerType(), a.getOwnerId(),
                a.getOriginalName(), a.getMimeType(), a.getFileSize(),
                a.getUploaderId(), a.getCreatedAt()
        );
    }
}
