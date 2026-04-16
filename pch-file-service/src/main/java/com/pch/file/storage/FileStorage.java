package com.pch.file.storage;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;

/**
 * 파일 스토리지 추상화. S3 / LocalDisk 구현체를 교체할 수 있다.
 */
public interface FileStorage {

    /**
     * 파일 저장.
     */
    void store(InputStream inputStream, String key, String mimeType, long size);

    /**
     * 파일 로드.
     */
    InputStream load(String key);

    /**
     * 파일 삭제.
     */
    void delete(String key);

    /**
     * Presigned 다운로드 URL 발급.
     */
    URL presignedDownloadUrl(String key, Duration ttl);
}
