package com.pch.file.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.time.Duration;

/**
 * 로컬 디스크 기반 FileStorage. 개발/테스트 환경 전용.
 */
@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local")
public class LocalDiskFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalDiskFileStorage.class);

    private final Path storagePath;

    public LocalDiskFileStorage(@Value("${aws.s3.local-storage-path:/tmp/pch-files}") String path) {
        this.storagePath = Paths.get(path);
        try {
            Files.createDirectories(storagePath);
        } catch (IOException e) {
            throw new UncheckedIOException("로컬 스토리지 디렉토리 생성 실패", e);
        }
    }

    @Override
    public void store(InputStream inputStream, String key, String mimeType, long size) {
        try {
            Path target = storagePath.resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("로컬 저장 완료: {}", target);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장 실패: " + key, e);
        }
    }

    @Override
    public InputStream load(String key) {
        try {
            return Files.newInputStream(storagePath.resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("파일 로드 실패: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(storagePath.resolve(key));
            log.info("로컬 삭제 완료: {}", key);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제 실패: " + key, e);
        }
    }

    @Override
    public URL presignedDownloadUrl(String key, Duration ttl) {
        try {
            return storagePath.resolve(key).toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException("URL 변환 실패: " + key, e);
        }
    }
}
