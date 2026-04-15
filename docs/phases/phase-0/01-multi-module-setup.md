# 멀티모듈 Gradle 프로젝트 구성

## 개요

PCH 모놀리스를 마이크로서비스 아키텍처로 전환하기 위해 Gradle 9.4 기반의 멀티모듈 프로젝트로 재구조화합니다. 각 모듈은 독립적으로 빌드되고 배포될 수 있으며, 공통 라이브러리를 통해 종속성을 관리합니다.

## 프로젝트 구조

```
pch-msa/
├── settings.gradle
├── build.gradle
├── gradle.properties
├── gradle/
│   └── wrapper/
├── pch-common/                   # 공유 라이브러리
│   ├── build.gradle
│   └── src/main/java/com/pch/common/
├── pch-gateway/                  # API Gateway
│   ├── build.gradle
│   └── src/main/java/com/pch/gateway/
├── pch-discovery/                # Eureka Server
│   ├── build.gradle
│   └── src/main/java/com/pch/discovery/
├── pch-auth/                     # Auth Service (Phase 1)
│   ├── build.gradle
│   └── src/main/java/com/pch/auth/
├── pch-project/                  # Project Service (Phase 1)
│   ├── build.gradle
│   └── src/main/java/com/pch/project/
├── pch-issue/                    # Issue Service (Phase 2)
│   ├── build.gradle
│   └── src/main/java/com/pch/issue/
├── pch-notification/             # Notification Service (Phase 1)
│   ├── build.gradle
│   └── src/main/java/com/pch/notification/
├── pch-file/                     # File Service (Phase 1)
│   ├── build.gradle
│   └── src/main/java/com/pch/file/
├── pch-integration/              # Integration Service (Phase 1)
│   ├── build.gradle
│   └── src/main/java/com/pch/integration/
└── docker/
    └── docker-compose.yml
```

## settings.gradle

Root 설정 파일로 모든 서브프로젝트를 정의합니다.

```gradle
rootProject.name = 'pch-msa'

include 'pch-common'
include 'pch-gateway'
include 'pch-discovery'
include 'pch-auth'
include 'pch-project'
include 'pch-issue'
include 'pch-notification'
include 'pch-file'
include 'pch-integration'

// Phase 2에서 추가될 모듈
// include 'pch-comment'
// include 'pch-timeline'
// include 'pch-report'

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            url "https://repo.spring.io/milestone"
        }
    }
}
```

## Root build.gradle

모든 모듈이 공통으로 적용할 설정과 의존성 버전을 정의합니다.

```gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.3' apply false
    id 'io.spring.dependency-management' version '1.1.4' apply false
}

allprojects {
    group = 'com.pch'
    version = '1.0.0-SNAPSHOT'

    repositories {
        mavenCentral()
        maven {
            url "https://repo.spring.io/milestone"
        }
    }
}

// 모든 Java 프로젝트에 적용
subprojects {
    apply plugin: 'java'
    apply plugin: 'io.spring.dependency-management'

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencyManagement {
        imports {
            mavenBom 'org.springframework.boot:spring-boot-dependencies:4.0.3'
            mavenBom 'org.springframework.cloud:spring-cloud-dependencies:2025.0.0'
        }
    }

    dependencies {
        // 공통 테스트 의존성
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        testImplementation 'org.junit.jupiter:junit-jupiter-api'
        testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine'
    }

    test {
        useJUnitPlatform()
    }
}

// Spring Boot 애플리케이션 플러그인이 적용된 모듈들
configure(subprojects.findAll { it.name != 'pch-common' }) {
    apply plugin: 'org.springframework.boot'

    dependencies {
        implementation 'org.springframework.boot:spring-boot-starter-actuator'
        implementation 'org.springframework.boot:spring-boot-starter-web'
        implementation 'io.micrometer:micrometer-registry-prometheus'
    }
}

// pch-common은 Spring Boot 플러그인을 적용하지 않음
project(':pch-common') {
    apply plugin: 'java-library'
    
    dependencies {
        // pch-common의 공개 API
        api 'org.springframework.boot:spring-boot-starter'
    }
}

// 각 서비스는 pch-common에 의존
subprojects.findAll { it.name.startsWith('pch-') && it.name != 'pch-common' }.each { svc ->
    svc.dependencies {
        implementation project(':pch-common')
    }
}
```

## gradle.properties

빌드 성능 최적화 설정

```properties
# Gradle 성능
org.gradle.caching=true
org.gradle.parallel=true
org.gradle.workers.max=8
org.gradle.jvmargs=-Xmx2g

# 프로젝트 속성
project.version=1.0.0-SNAPSHOT

# Spring Boot
spring-boot.build-image.publish=false
```

## 모듈별 build.gradle 예시

### pch-gateway의 build.gradle

```gradle
dependencies {
    implementation 'org.springframework.cloud:spring-cloud-starter-gateway'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
    implementation 'io.github.resilience4j:resilience4j-spring-cloud-gateway:2.1.0'
    implementation 'io.github.resilience4j:resilience4j-circuitbreaker:2.1.0'
}
```

### pch-auth의 build.gradle

```gradle
dependencies {
    implementation project(':pch-common')
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'mysql:mysql-connector-java:8.0.33'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.3'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.3'
}
```

## 공통 의존성 관리 전략

### 1. BOM(Bill of Materials) 활용

Spring Boot 및 Spring Cloud의 BOM을 통해 버전 호환성을 자동 관리합니다.

| BOM | 버전 | 목적 |
|-----|------|------|
| spring-boot-dependencies | 4.0.3 | Spring Boot 의존성 |
| spring-cloud-dependencies | 2025.0.0 | Spring Cloud 의존성 |

### 2. 플랫폼 의존성 (Platform Dependencies)

공통 라이브러리의 버전을 중앙에서 관리합니다.

```gradle
// Root build.gradle에 추가
subprojects {
    dependencyManagement {
        dependencies {
            // 직렬화/역직렬화
            dependency 'com.fasterxml.jackson.core:jackson-databind:2.16.0'
            
            // JSON Web Token
            dependency 'io.jsonwebtoken:jjwt-api:0.12.3'
            
            // 데이터베이스
            dependency 'mysql:mysql-connector-java:8.0.33'
            dependency 'org.mariadb.jdbc:mariadb-java-client:3.1.4'
            
            // HTTP 클라이언트
            dependency 'org.apache.httpcomponents.client5:httpclient5:5.2.1'
            
            // Resilience4j
            dependency 'io.github.resilience4j:resilience4j-bom:2.1.0'
        }
    }
}
```

### 3. 모듈 간 의존성 규칙

| 모듈 | 의존성 | 설명 |
|------|--------|------|
| pch-common | 없음 | 어떤 모듈도 의존할 수 없음 |
| pch-gateway | pch-common | 라우팅만 담당 |
| pch-discovery | pch-common | Eureka 서버 |
| pch-auth | pch-common | Auth 서비스 |
| pch-project | pch-common | Project 서비스 |
| pch-issue | pch-common | Issue 서비스 |
| pch-notification | pch-common | Notification 서비스 |
| pch-file | pch-common | File 서비스 |
| pch-integration | pch-common | Integration 서비스 |

**중요**: 비즈니스 서비스 간 직접 의존성은 금지됩니다. 모두 pch-common의 이벤트와 DTO를 통해 통신합니다.

## 빌드 명령어

### 전체 빌드

```bash
# 전체 모든 모듈 빌드
./gradlew build

# 테스트 스킵하고 빌드
./gradlew build -x test

# 캐시 무효화하고 빌드
./gradlew build --no-build-cache
```

### 개별 모듈 빌드

```bash
# pch-auth 모듈만 빌드
./gradlew :pch-auth:build

# pch-gateway 모듈만 빌드
./gradlew :pch-gateway:build

# pch-common 라이브러리만 빌드
./gradlew :pch-common:build
```

### 실행

```bash
# pch-gateway 실행
./gradlew :pch-gateway:bootRun

# pch-auth 서비스 실행
./gradlew :pch-auth:bootRun

# 특정 프로파일로 실행
./gradlew :pch-auth:bootRun --args='--spring.profiles.active=dev'
```

### 의존성 트리 확인

```bash
# 전체 의존성 트리
./gradlew dependencies

# 특정 모듈의 의존성만 확인
./gradlew :pch-auth:dependencies

# 충돌 분석
./gradlew dependencyInsight --dependency com.fasterxml.jackson
```

## 주의사항

### 1. 순환 의존성 방지

```gradle
// ❌ 금지: pch-auth가 pch-project에 의존하고, pch-project가 pch-auth에 의존
// pch-auth의 build.gradle
dependencies {
    implementation project(':pch-project')  // 절대 금지!
}
```

### 2. pch-common 수정 시 신중함

pch-common은 모든 모듈의 기반이므로, 변경 시 하위 호환성을 유지해야 합니다.

```java
// ❌ 금지: Enum 값 제거
public enum IssueStatus {
    OPEN,
    // CLOSED  <- 제거하면 기존 코드 깨짐
}

// ✅ 허용: Enum 값 추가
public enum IssueStatus {
    OPEN,
    CLOSED,
    ARCHIVED  // 새로 추가
}
```

### 3. 버전 충돌

```bash
# 의존성 버전 충돌 발생 시
./gradlew dependencyInsight --dependency org.springframework.boot

# 또는 excludes로 해결
dependencies {
    implementation('org.springframework.cloud:spring-cloud-starter-gateway') {
        exclude group: 'org.springframework.boot', module: 'spring-boot-starter-logging'
    }
}
```

## 체크리스트

- [ ] `settings.gradle` 작성 (11개 모듈 정의)
- [ ] Root `build.gradle` 작성 (BOM 설정)
- [ ] `gradle.properties` 작성 (성능 설정)
- [ ] 각 모듈별 `build.gradle` 작성
- [ ] `./gradlew build` 성공 확인
- [ ] IDE에서 모듈 인식 확인
- [ ] 의존성 충돌 없음 확인
- [ ] 순환 의존성 제거 확인

---

**Last Updated**: 2026-04-15  
**Version**: 1.0  
**Related Documents**: 
- [00-phase-0-overview.md](00-phase-0-overview.md)
- [02-common-library.md](02-common-library.md)
