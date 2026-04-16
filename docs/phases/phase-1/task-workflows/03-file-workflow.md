# T3 — File Service 분리 워크플로우

> 목표: 첨부파일 업로드/다운로드/삭제 로직을 **`pch-file-service`** 로 분리하고, S3 호환 스토리지를 통해 파일 본체를 저장한다.
>
> **브랜치**: `feature/phase-1-file` · **베이스**: `develop` · **예상 기간**: 2~3일

---

## 🧩 Prerequisites

- [ ] T1 (Auth) 완료 — `X-User-Id` 헤더로 업로더 식별
- [ ] 로컬 S3 대체(LocalStack 또는 MinIO) 컨테이너 기동
- [ ] `pch_file` DB 준비 (`docker/init-db.sql`)
- [ ] 파일 크기 상한 / 허용 MIME 정책 합의

---

## 🌿 브랜치 & 작업 순서

```bash
git checkout develop && git pull
git checkout -b feature/phase-1-file
```

### Step 1. 도메인/엔티티 (0.5일)

- [ ] `Attachment` (id, ownerType, ownerId, originalName, storedKey, mimeType, size, uploaderId, createdAt)
  - `ownerType` = `ISSUE | COMMENT | USER_AVATAR | PROJECT_AVATAR`
- [ ] `db/migration/V1__create_attachments.sql`
- **커밋**: `feat(file): Attachment 엔티티 정의 + Flyway 초기 마이그레이션`

### Step 2. 스토리지 추상화 (0.5일)

```java
public interface FileStorage {
    StoredObject store(InputStream in, String key, String mimeType, long size);
    InputStream load(String key);
    void delete(String key);
    URL presignedUploadUrl(String key, Duration ttl);
    URL presignedDownloadUrl(String key, Duration ttl);
}

@Component("s3") class S3FileStorage implements FileStorage { ... }
@Component("local") class LocalDiskFileStorage implements FileStorage { ... }  // 테스트용
```

- [ ] `spring.file.storage=s3|local` 기반 Conditional 빈
- [ ] AWS SDK v2, AssumeRole + KMS SSE (prod)
- **커밋**: `feat(file): FileStorage 추상화 + S3/LocalDisk 구현`

### Step 3. Upload / Download API (1일)

| 메서드 | 경로                                                  | 기능                               |
|--------|-------------------------------------------------------|------------------------------------|
| POST   | `/api/v1/attachments/presign`                         | Presigned URL 발급                 |
| POST   | `/api/v1/attachments` (multipart)                     | 서버 경유 업로드 (소형 파일)         |
| GET    | `/api/v1/attachments/{id}`                            | 메타데이터 조회                    |
| GET    | `/api/v1/attachments/{id}/download`                   | 다운로드 (302 → presigned URL)     |
| DELETE | `/api/v1/attachments/{id}`                            | 삭제 (Soft → 배치 영구삭제)         |
| GET    | `/api/v1/issues/{issueId}/attachments`                | 이슈에 첨부된 파일 목록             |

- [ ] 업로드 시 MIME/확장자 화이트리스트
  - `image/*, application/pdf, application/zip, text/*` (운영 정책 반영)
- [ ] 크기 제한 (기본 20MB, profile 별 override)
- [ ] 스캔: ClamAV REST (Phase 2 도입, 지금은 hook point)
- **커밋**: `feat(file): Attachment 업로드/다운로드 API + presigned URL`

### Step 4. 이벤트 구독 (0.5일)

- [ ] `IssueDeletedEvent` 수신 → 해당 이슈의 첨부 **soft-delete** + 스토리지 삭제 배치
- [ ] 배치: `@Scheduled(cron = "0 0 3 * * *")` 7일 이상 soft-delete 된 것 영구삭제
- **커밋**: `feat(file): IssueDeleted 이벤트 구독 및 삭제 배치 구현`

### Step 5. 테스트 (0.5일)

- [ ] `@SpringBootTest` + `@LocalStackContainer` : 실 S3 호환 스토리지로 통합 테스트
- [ ] 업로드 → 메타데이터 저장 → presigned URL 로 다운로드 검증
- [ ] 큰 파일(10MB+) 스트리밍 확인 (OOM 없음)
- **커밋**: `test(file): LocalStack 기반 S3 통합 테스트`

---

## 💻 핵심 코드 스니펫

```java
@PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<AttachmentResponse> upload(
        @RequestPart("file") MultipartFile file,
        @RequestPart("owner") AttachmentOwnerRequest owner
) {
    Long uploaderId = SecurityContextUtil.getCurrentUserId();
    return ApiResponse.success(service.upload(uploaderId, owner, file));
}
```

---

## 🧪 테스트 시나리오

| # | 시나리오                            | 예상 결과                                     |
|---|-------------------------------------|-----------------------------------------------|
| 1 | 5MB PDF 업로드                      | 200, Attachment 저장, S3 객체 존재             |
| 2 | 25MB 업로드                         | 413 Payload Too Large                          |
| 3 | `.exe` 업로드                       | 400 Unsupported media type                     |
| 4 | 남의 첨부 DELETE                     | 403 Forbidden                                  |
| 5 | `IssueDeletedEvent` 수신             | 관련 Attachment 가 soft-delete                  |
| 6 | 배치 실행 (7일 경과 soft-delete)     | S3 객체 삭제 + DB 레코드 영구삭제               |

---

## ✅ DoD (File 전용 추가)

- [ ] 업로드 MIME/Magic Number 이중 검증
- [ ] 파일명 XSS 방어 (Content-Disposition 이스케이프)
- [ ] 퍼블릭 URL **금지**. 반드시 presigned URL 발급
- [ ] 스토리지 비밀키 env 주입 (Jenkins Secret / SSM)
- [ ] 감사로그: 업로드/삭제 이벤트를 `file.audit` 토픽으로 발행 (Phase 2 에서 감사 서비스가 소비)

---

## ⚠️ 리스크 & 대응

| 리스크                     | 영향 | 대응                                       |
|----------------------------|------|--------------------------------------------|
| 악성 파일 업로드            | 고   | ClamAV 스캔(Phase 2), MIME 화이트리스트     |
| S3 비용 폭증               | 중   | Lifecycle policy (30일 후 Glacier)          |
| 대용량 파일 메모리 누수     | 중   | 스트리밍(InputStream) 사용, Temp 파일 정리   |
| Orphan 파일                | 중   | 이벤트 소실 대비 주간 reconcile 배치          |

---

**Last Updated**: 2026-04-16 · **Version**: 1.0
