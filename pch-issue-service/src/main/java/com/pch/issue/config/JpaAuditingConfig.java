package com.pch.issue.config;

import java.util.Optional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.pch.common.security.CurrentUser;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Component("auditorAware")
    public static class HeaderAuditorAware implements AuditorAware<Long> {
        @Override
        public Optional<Long> getCurrentAuditor() {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return Optional.empty();
            String userId = attrs.getRequest().getHeader(CurrentUser.HEADER_USER_ID);
            if (userId == null || userId.isBlank()) return Optional.empty();
            try {
                return Optional.of(Long.valueOf(userId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
    }
}
