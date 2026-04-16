package com.pch.file.batch;

import com.pch.file.domain.Attachment;
import com.pch.file.repository.AttachmentRepository;
import com.pch.file.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 7일 이상 soft-delete 된 첨부파일을 영구 삭제하는 배치.
 * 매일 새벽 3시 실행.
 */
@Component
@RequiredArgsConstructor
public class AttachmentCleanupBatch {

    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanupBatch.class);
    private static final int RETENTION_DAYS = 7;

    private final AttachmentRepository attachmentRepository;
    private final FileStorage fileStorage;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupDeletedAttachments() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(RETENTION_DAYS);
        List<Attachment> targets = attachmentRepository.findSoftDeletedBefore(threshold);

        if (targets.isEmpty()) {
            log.info("영구 삭제 대상 없음");
            return;
        }

        log.info("영구 삭제 시작: {} 건", targets.size());
        for (Attachment attachment : targets) {
            try {
                fileStorage.delete(attachment.getStoredKey());
                attachmentRepository.delete(attachment);
                log.debug("영구 삭제 완료: id={}, key={}", attachment.getId(), attachment.getStoredKey());
            } catch (Exception e) {
                log.error("영구 삭제 실패: id={}, key={}", attachment.getId(), attachment.getStoredKey(), e);
            }
        }
        log.info("영구 삭제 완료: {} 건 처리", targets.size());
    }
}
