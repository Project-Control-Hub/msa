package com.pch.integration.webhook;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IssueKeyExtractorTest {

    private final IssueKeyExtractor extractor = new IssueKeyExtractor();

    @Test
    @DisplayName("커밋 메시지에서 이슈 키 추출")
    void extract_fromCommitMessage() {
        List<String> keys = extractor.extract("PCH-100 fix login bug");
        assertThat(keys).containsExactly("PCH-100");
    }

    @Test
    @DisplayName("여러 이슈 키 추출 + 중복 제거")
    void extract_multipleKeys() {
        List<String> keys = extractor.extract("Closes PCH-100, PCH-200, also PCH-100");
        assertThat(keys).containsExactly("PCH-100", "PCH-200");
    }

    @Test
    @DisplayName("이슈 키 없는 텍스트")
    void extract_noKeys() {
        assertThat(extractor.extract("just a regular commit")).isEmpty();
    }
}
