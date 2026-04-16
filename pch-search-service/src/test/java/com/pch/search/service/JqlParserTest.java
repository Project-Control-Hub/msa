package com.pch.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JqlParserTest {

    private JqlParser parser;

    @BeforeEach
    void setUp() {
        parser = new JqlParser();
    }

    @Test
    @DisplayName("단일 등호 조건 파싱: status = OPEN")
    void parseEqualsClause() {
        Query query = parser.parse("status = OPEN");
        assertNotNull(query);
        assertTrue(query.isTerm());
    }

    @Test
    @DisplayName("AND 복합 조건 파싱: status = OPEN AND type = BUG")
    void parseAndClauses() {
        Query query = parser.parse("status = OPEN AND type = BUG");
        assertNotNull(query);
        assertTrue(query.isBool());
    }

    @Test
    @DisplayName("OR 복합 조건 파싱: status = OPEN OR status = IN_PROGRESS")
    void parseOrClauses() {
        Query query = parser.parse("status = OPEN OR status = IN_PROGRESS");
        assertNotNull(query);
        assertTrue(query.isBool());
    }

    @Test
    @DisplayName("contains (~) 연산자: summary ~ 로그인")
    void parseContainsClause() {
        Query query = parser.parse("summary ~ 로그인");
        assertNotNull(query);
        assertTrue(query.isMatch());
    }

    @Test
    @DisplayName("빈 JQL은 match_all 반환")
    void parseEmptyJql() {
        Query query = parser.parse("");
        assertNotNull(query);
        assertTrue(query.isMatchAll());
    }

    @Test
    @DisplayName("null JQL은 match_all 반환")
    void parseNullJql() {
        Query query = parser.parse(null);
        assertNotNull(query);
        assertTrue(query.isMatchAll());
    }

    @Test
    @DisplayName("부정 조건: status != DONE")
    void parseNotEqualsClause() {
        Query query = parser.parse("status != DONE");
        assertNotNull(query);
        assertTrue(query.isBool());
    }

    @Test
    @DisplayName("숫자 필드 등호: assigneeId = 42")
    void parseNumericField() {
        Query query = parser.parse("assigneeId = 42");
        assertNotNull(query);
        assertTrue(query.isTerm());
    }
}
