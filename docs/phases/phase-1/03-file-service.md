# File Service 분리 작업 명세

## 서비스 개요

File Service는 첨부파일의 업로드, 다운로드, 삭제를 담당합니다. S3와 Local 스토리지를 추상화하여 지원합니다.

## 기본 정보

| 항목 | 값 |
|------|-----|
| **서비스명** | pch-file |
| **포트** | 8087 |
| **DB** | pch_file (MySQL 8.0) |
| **난이도** | ★☆☆☆☆ |
| **소요시간** | 2-3일 |

## 서비스 책임 영역

### 보유 엔티티

| 엔티티 | 테이블명 | 설명 |
|--------|----------|------|
| Attachment | attachment_tb | 업로드된 파일 메타데이터 |

## API 엔드포인트

| Method | Path | 설명 | 응답 |
|--------|------|------|------|
| POST | `/api/v1/files/upload` | 파일 업로드 | FileResponse |
| GET | `/api/v1/files/{id}` | 파일 정보 조회 | FileResponse |
| GET | `/api/v1/files/{id}/download` | 파일 다운로드 | Binary |
| DELETE | `/api/v1/files/{id}` | 파일 삭제 | success |

### 내부 API

| Method | Path | 설명 |
|--------|------|------|
| POST | `/internal/v1/files/cleanup` | 이슈 삭제 시 연관 파일 정리 |

## 구독 이벤트

| Topic | Event | 처리 로직 |
|-------|-------|---------|
| issue.deleted | IssueDeletedEvent | 연관 파일 삭제 |

## 데이터베이스 스키마

### attachment_tb

```sql
CREATE TABLE attachment_tb (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    storage_type ENUM('LOCAL', 'S3') DEFAULT 'LOCAL',
    entity_type VARCHAR(50),  -- ISSUE, PROJECT 등
    entity_id BIGINT,
    uploaded_by_user_id BIGINT NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    deleted_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_entity_type_id ON attachment_tb(entity_type, entity_id);
CREATE INDEX idx_uploaded_by_user_id ON attachment_tb(uploaded_by_user_id);
```

## 작업 체크리스트

### 1단계: 설계 (0.5일)
- [ ] 파일 저장소 선택 (Local, S3)
- [ ] 최대 파일 크기 정의 (예: 50MB)
- [ ] 허용 파일 타입 정의
- [ ] 파일명 규칙 정의 (UUID 기반)

### 2단계: 코드 구현 (1.5일)
- [ ] Entity, Repository, Service 구현
  ```java
  @Entity
  public class Attachment {
      @Id @GeneratedValue
      private Long id;
      private String fileName;
      private String originalName;
      private Long fileSize;
      private String fileType;
      private String storagePath;
      @Enumerated(EnumType.STRING)
      private StorageType storageType;  // LOCAL, S3
      private String entityType;
      private Long entityId;
      private Long uploadedByUserId;
      // ...
  }
  
  @Service
  public class FileService {
      public FileResponse uploadFile(MultipartFile file, String entityType, Long entityId) { }
      public Resource downloadFile(Long fileId) { }
      public void deleteFile(Long fileId) { }
  }
  ```

- [ ] 스토리지 추상화
  ```java
  public interface StorageProvider {
      String uploadFile(MultipartFile file) throws IOException;
      Resource downloadFile(String storagePath) throws IOException;
      void deleteFile(String storagePath) throws IOException;
  }
  
  @Component
  public class LocalStorageProvider implements StorageProvider {
      private static final String UPLOAD_DIR = "/data/uploads";
      
      @Override
      public String uploadFile(MultipartFile file) throws IOException {
          String fileName = UUID.randomUUID().toString();
          Path path = Paths.get(UPLOAD_DIR, fileName);
          Files.write(path, file.getBytes());
          return fileName;
      }
  }
  
  @Component
  public class S3StorageProvider implements StorageProvider {
      private final AmazonS3 s3Client;
      
      @Override
      public String uploadFile(MultipartFile file) throws IOException {
          String fileName = UUID.randomUUID().toString();
          s3Client.putObject(BUCKET_NAME, fileName, file.getInputStream(), null);
          return fileName;
      }
  }
  ```

- [ ] Controller 구현
  ```java
  @RestController
  @RequestMapping("/api/v1/files")
  public class FileController {
      @PostMapping("/upload")
      public ApiResponse<FileResponse> uploadFile(
          @RequestParam MultipartFile file,
          @RequestParam String entityType,
          @RequestParam Long entityId) { }
      
      @GetMapping("/{id}")
      public ApiResponse<FileResponse> getFile(@PathVariable Long id) { }
      
      @GetMapping("/{id}/download")
      public ResponseEntity<Resource> downloadFile(@PathVariable Long id) { }
      
      @DeleteMapping("/{id}")
      public ApiResponse<Void> deleteFile(@PathVariable Long id) { }
  }
  ```

- [ ] Event Listener 구현
  ```java
  @Component
  public class FileEventListener {
      @KafkaListener(topics = "issue.deleted", groupId = "pch-file")
      public void handleIssueDeleted(IssueDeletedEvent event) {
          // 이슈 관련 모든 파일 삭제
          List<Attachment> attachments = 
              attachmentRepository.findByEntityTypeAndEntityId("ISSUE", event.getIssueId());
          for (Attachment att : attachments) {
              deleteFile(att.getId());
          }
      }
  }
  ```

### 3단계: 테스트 (1일)
- [ ] 단위 테스트
  ```java
  @Test
  public void testUploadFile() {
      MockMultipartFile file = new MockMultipartFile(
          "file", "test.txt", "text/plain", "content".getBytes());
      FileResponse response = fileService.uploadFile(file, "ISSUE", 1L);
      assertNotNull(response.getId());
  }
  
  @Test
  public void testDownloadFile() {
      Resource resource = fileService.downloadFile(1L);
      assertTrue(resource.exists());
  }
  
  @Test
  public void testDeleteFile() {
      fileService.deleteFile(1L);
      assertFalse(attachmentRepository.existsById(1L));
  }
  ```

- [ ] 파일 크기 검증
  - 최대 크기 초과 시 거부
  - 예: 50MB 제한

- [ ] 파일 타입 검증
  - 허용된 타입만 업로드
  - 예: PDF, DOC, XLS, JPG, PNG

- [ ] 스토리지 테스트
  - Local 스토리지 저장/조회/삭제
  - S3 저장/조회/삭제 (Mock)

### 4단계: 데이터베이스 설정 (0.5일)
- [ ] pch_file 데이터베이스 생성
- [ ] attachment_tb 생성
- [ ] init-db.sql에 추가

### 5단계: Gateway 라우팅 (0.5일)
- [ ] File Service 라우팅 규칙 추가
  ```yaml
  - id: file-service
    uri: lb://pch-file
    predicates:
      - Path=/api/v1/files/**
    filters:
      - name: RequestRateLimiter
        args:
          redis-rate-limiter:
            replenishRate: 10
            burstCapacity: 20
  ```

### 6단계: E2E 테스트 (0.5일)
- [ ] 파일 업로드 → 다운로드 → 삭제 시나리오
  ```bash
  # 1. 파일 업로드
  curl -X POST http://localhost:8000/api/v1/files/upload \
    -H "Authorization: Bearer <token>" \
    -F "file=@document.pdf" \
    -F "entityType=ISSUE" \
    -F "entityId=1"
  
  # 2. 파일 조회
  curl http://localhost:8000/api/v1/files/1 \
    -H "Authorization: Bearer <token>"
  
  # 3. 파일 다운로드
  curl http://localhost:8000/api/v1/files/1/download \
    -H "Authorization: Bearer <token>" \
    -o document.pdf
  
  # 4. 파일 삭제
  curl -X DELETE http://localhost:8000/api/v1/files/1 \
    -H "Authorization: Bearer <token>"
  ```

- [ ] Issue 삭제 시 파일 자동 정리
  ```bash
  # 이슈 삭제
  curl -X DELETE http://localhost:8000/api/v1/issues/1 \
    -H "Authorization: Bearer <token>"
  
  # 파일이 자동으로 삭제되었는지 확인
  # → 이벤트 처리 1-2초 후 파일 삭제됨
  ```

## 설정

### application.yml

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB
  
  datasource:
    url: jdbc:mysql://mysql:3306/pch_file
    username: pch_file
    password: ${MYSQL_PASSWORD}

# 파일 저장소 설정
file:
  storage:
    type: LOCAL  # LOCAL 또는 S3
    local:
      upload-dir: /data/uploads
    s3:
      region: ap-northeast-2
      bucket-name: pch-files
      access-key: ${AWS_ACCESS_KEY}
      secret-key: ${AWS_SECRET_KEY}
```

## 주의사항

### 보안

1. **파일 타입 검증**
   ```java
   private static final Set<String> ALLOWED_TYPES = Set.of(
       "application/pdf",
       "application/msword",
       "application/vnd.ms-excel",
       "image/jpeg",
       "image/png"
   );
   
   if (!ALLOWED_TYPES.contains(file.getContentType())) {
       throw new InvalidFileTypeException();
   }
   ```

2. **파일명 안전화**
   ```java
   String fileName = UUID.randomUUID().toString();
   // 원본 파일명은 메타데이터에만 저장
   attachment.setOriginalName(file.getOriginalFilename());
   ```

3. **접근 제어**
   ```java
   // 파일 다운로드 시 권한 확인
   public Resource downloadFile(Long fileId, Long userId) {
       Attachment attachment = attachmentRepository.findById(fileId)
           .orElseThrow(FileNotFoundException::new);
       
       // 파일 소유자 또는 프로젝트 멤버만 다운로드 가능
       verifyAccess(attachment, userId);
       
       return storageProvider.downloadFile(attachment.getStoragePath());
   }
   ```

### 성능

1. **클라우드 스토리지 (S3)**
   - 대량의 파일은 S3 권장
   - Cost 효율적
   - 확장성 우수

2. **로컬 스토리지**
   - 개발/테스트 환경에서 사용
   - 프로덕션에서는 NFS 또는 S3 권장

### 신뢰성

1. **파일 삭제 실패 처리**
   ```java
   @Retry(maxAttempts = 3)
   public void deleteFile(Long fileId) {
       try {
           storageProvider.deleteFile(path);
       } catch (IOException e) {
           // 재시도
           throw new FileOperationException();
       }
   }
   ```

## 분리 후 모놀리스 정리

- [ ] `com.pch.domain.file` 패키지 삭제
- [ ] `com.pch.api.v1.file` 패키지 삭제
- [ ] attachment_tb 마이그레이션
- [ ] 파일 저장소 마이그레이션 (S3 업로드)

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**:
- [00-phase-1-overview.md](00-phase-1-overview.md)
- [../phase-0/05-kafka-setup.md](../phase-0/05-kafka-setup.md)
