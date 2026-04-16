package com.pch.file.service;

import com.pch.common.exception.BusinessException;
import com.pch.common.exception.ErrorCode;
import com.pch.file.domain.Attachment;
import com.pch.file.domain.OwnerType;
import com.pch.file.dto.AttachmentOwnerRequest;
import com.pch.file.dto.AttachmentResponse;
import com.pch.file.storage.FileStorage;
import com.pch.file.repository.AttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20MB
    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of(
            "image/", "application/pdf", "application/zip",
            "application/x-zip-compressed", "text/"
    );

    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    @Transactional
    public AttachmentResponse upload(Long uploaderId, AttachmentOwnerRequest owner, MultipartFile file) {
        validateFile(file);

        String storedKey = generateStoredKey(owner.ownerType(), file.getOriginalFilename());

        try {
            fileStorage.store(file.getInputStream(), storedKey, file.getContentType(), file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("파일 업로드 실패", e);
        }

        Attachment attachment = Attachment.create(
                owner.ownerType(), owner.ownerId(),
                sanitizeFilename(file.getOriginalFilename()),
                storedKey, file.getContentType(), file.getSize(), uploaderId
        );
        attachmentRepository.save(attachment);
        log.info("첨부파일 업로드: id={}, key={}", attachment.getId(), storedKey);

        return AttachmentResponse.from(attachment);
    }

    public AttachmentResponse getAttachment(Long id) {
        Attachment attachment = findActiveById(id);
        return AttachmentResponse.from(attachment);
    }

    public URL getDownloadUrl(Long id) {
        Attachment attachment = findActiveById(id);
        return fileStorage.presignedDownloadUrl(attachment.getStoredKey(), Duration.ofMinutes(10));
    }

    public List<AttachmentResponse> getAttachmentsByOwner(OwnerType ownerType, Long ownerId) {
        return attachmentRepository.findByOwnerTypeAndOwnerIdAndDeletedFalse(ownerType, ownerId)
                .stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Attachment attachment = findActiveById(id);
        if (!attachment.getUploaderId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        attachment.softDelete();
        log.info("첨부파일 soft-delete: id={}", id);
    }

    @Transactional
    public int softDeleteByOwner(OwnerType ownerType, Long ownerId) {
        return attachmentRepository.softDeleteByOwner(ownerType, ownerId);
    }

    private Attachment findActiveById(Long id) {
        Attachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));
        if (attachment.isDeleted()) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        }
        return attachment;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String mimeType = file.getContentType();
        if (mimeType == null || ALLOWED_MIME_PREFIXES.stream().noneMatch(mimeType::startsWith)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String generateStoredKey(OwnerType ownerType, String originalName) {
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        return ownerType.name().toLowerCase() + "/" + UUID.randomUUID() + ext;
    }

    /**
     * 파일명 XSS 방어: 위험 문자 제거.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[<>"';&|]", "_");
    }
}
