package com.pch.integration.webhook;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 커밋 메시지나 PR 본문에서 PCH-1234 패턴의 이슈 키를 추출.
 */
@Component
public class IssueKeyExtractor {

    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile("(PCH-\\d+)", Pattern.CASE_INSENSITIVE);

    public List<String> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<String> keys = new ArrayList<>();
        Matcher matcher = ISSUE_KEY_PATTERN.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1).toUpperCase());
        }
        return keys.stream().distinct().toList();
    }
}
