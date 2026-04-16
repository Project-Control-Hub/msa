package com.pch.file.controller;

import com.pch.common.response.ApiResponse;
import com.pch.file.domain.OwnerType;
import com.pch.file.dto.AttachmentOwnerRequest;
import com.pch.file.dto.AttachmentResponse;
import com.pch.file.service.AttachmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.net.URL;
import java.util.List;

@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    /**
     * 서버 경유 업로드 (소형 파일).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AttachmentResponse> upload(
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestPart("owner") @Valid AttachmentOwnerRequest owner,
            @RequestHeader("X-User-Id") Long uploaderId
    ) {
        return ApiResponse.success(attachmentService.upload(uploaderId, owner, file));
    }

    /**
     * 메타데이터 조회.
     */
    @GetMapping("/{id}")
    public ApiResponse<AttachmentResponse> getAttachment(@PathVariable Long id) {
        return ApiResponse.success(attachmentService.getAttachment(id));
    }

    /**
     * 다운로드 (302 → presigned URL).
     */
    @GetMapping("/{id}/download")
    public org.springframework.http.ResponseEntity<Void> download(@PathVariable Long id) {
        URL downloadUrl = attachmentService.getDownloadUrl(id);
        return org.springframework.http.ResponseEntity
                .status(HttpStatus.FOUND)
                .location(java.net.URI.create(downloadUrl.toString()))
                .build();
    }

    /**
     * 특정 소유자의 첨부파일 목록 조회.
     */
    @GetMapping
    public ApiResponse<List<AttachmentResponse>> getByOwner(
            @RequestParam OwnerType ownerType,
            @RequestParam Long ownerId
    ) {
        return ApiResponse.success(attachmentService.getAttachmentsByOwner(ownerType, ownerId));
    }

    /**
     * 삭제 (soft-delete).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long requesterId
    ) {
        attachmentService.delete(id, requesterId);
        return ApiResponse.success(null);
    }
}
