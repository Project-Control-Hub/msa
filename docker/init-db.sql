-- PCH MSA: 서비스별 데이터베이스 초기 생성
-- Docker Compose 최초 실행 시 자동 적용

CREATE DATABASE IF NOT EXISTS pch_auth
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_project
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_issue
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_board_report
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_search
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_notification
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_file
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pch_integration
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 서비스별 전용 사용자 (운영 환경에서는 별도 시크릿 관리)
CREATE USER IF NOT EXISTS 'pch_auth'@'%' IDENTIFIED BY 'authpass';
GRANT ALL PRIVILEGES ON pch_auth.* TO 'pch_auth'@'%';

CREATE USER IF NOT EXISTS 'pch_project'@'%' IDENTIFIED BY 'projectpass';
GRANT ALL PRIVILEGES ON pch_project.* TO 'pch_project'@'%';

CREATE USER IF NOT EXISTS 'pch_issue'@'%' IDENTIFIED BY 'issuepass';
GRANT ALL PRIVILEGES ON pch_issue.* TO 'pch_issue'@'%';

CREATE USER IF NOT EXISTS 'pch_board_report'@'%' IDENTIFIED BY 'boardpass';
GRANT ALL PRIVILEGES ON pch_board_report.* TO 'pch_board_report'@'%';

CREATE USER IF NOT EXISTS 'pch_search'@'%' IDENTIFIED BY 'searchpass';
GRANT ALL PRIVILEGES ON pch_search.* TO 'pch_search'@'%';

CREATE USER IF NOT EXISTS 'pch_notification'@'%' IDENTIFIED BY 'notifpass';
GRANT ALL PRIVILEGES ON pch_notification.* TO 'pch_notification'@'%';

CREATE USER IF NOT EXISTS 'pch_file'@'%' IDENTIFIED BY 'filepass';
GRANT ALL PRIVILEGES ON pch_file.* TO 'pch_file'@'%';

CREATE USER IF NOT EXISTS 'pch_integration'@'%' IDENTIFIED BY 'integrationpass';
GRANT ALL PRIVILEGES ON pch_integration.* TO 'pch_integration'@'%';

FLUSH PRIVILEGES;
