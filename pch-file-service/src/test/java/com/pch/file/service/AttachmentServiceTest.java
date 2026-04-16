package com.pch.file.service;

import com.pch.common.exception.BusinessException;
import com.pch.file.domain.Attachment;
import com.pch.file.domain.OwnerType;
import com.pch.file.dto.AttachmentOwnerRequest;
import com.pch.file.dto.AttachmentResponse;
import com.pch.file.repository.AttachmentRepository;
import com.pch.file.storage.FileStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock AttachmentRepository attachmentRepository;
    @Mock FileStorage fileStorage;
    @InjectMocks AttachmentService attachmentService;

    @Test
    @DisplayName("PDF 파일 업로드 성공")
    void upload_success() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", new byte[1024]);
        AttachmentOwnerRequest owner = new AttachmentOwnerRequest(OwnerType.ISSUE, 1L);

        given(attachmentRepository.save(any(Attachment.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        AttachmentResponse response = attachmentService.upload(100L, owner, file);

        // then
        assertThat(response.originalName()).isEqualTo("test.pdf");
        assertThat(response.mimeType()).isEqualTo("application/pdf");
        assertThat(response.fileSize()).isEqualTo(1024L);
        then(fileStorage).should().store(any(), anyString(), eq("application/pdf"), eq(1024L));
    }

    @Test
    @DisplayName("25MB 파일 업로드 → 거부")
    void upload_tooLarge() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.pdf", "application/pdf", new byte[25 * 1024 * 1024]);
        AttachmentOwnerRequest owner = new AttachmentOwnerRequest(OwnerType.ISSUE, 1L);

        assertThatThrownBy(() -> attachmentService.upload(100L, owner, file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName(".exe 파일 업로드 → MIME 거부")
    void upload_unsupportedMime() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", new byte[100]);
        AttachmentOwnerRequest owner = new AttachmentOwnerRequest(OwnerType.ISSUE, 1L);

        assertThatThrownBy(() -> attachmentService.upload(100L, owner, file))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("타인의 첨부파일 삭제 시도 → FORBIDDEN")
    void delete_forbidden() {
        Attachment attachment = Attachment.create(
                OwnerType.ISSUE, 1L, "test.pdf", "key", "application/pdf", 1024L, 100L);
        // Use reflection to set ID
        try {
            var idField = Attachment.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(attachment, 1L);
        } catch (Exception ignored) {}

        given(attachmentRepository.findById(1L)).willReturn(Optional.of(attachment));

        assertThatThrownBy(() -> attachmentService.delete(1L, 999L))
                .isInstanceOf(BusinessException.class);
    }
}
