package com.pch.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JQL(Jira Query Language) 문자열을 Elasticsearch BoolQuery로 변환하는 파서.
 *
 * 지원 연산자: =, !=, IN, NOT IN, >=, <=, ~ (contains)
 * 지원 필드: status, type, priority, assigneeId, reporterId, projectKey, sprintId, labels
 * 복합 조건: AND, OR
 *
 * 예: "status = IN_PROGRESS AND priority >= HIGH AND projectKey = PCH"
 */
@Slf4j
@Component
public class JqlParser {

    private static final Pattern CLAUSE_PATTERN = Pattern.compile(
            "(\w+)\s*(=|!=|>=|<=|~|IN|NOT\s+IN)\s*(.+?)(?=\s+(?:AND|OR)\s+|$)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IN_VALUES_PATTERN = Pattern.compile(
            "\(([^)]+)\)"
    );

    private static final Set<String> KEYWORD_FIELDS = Set.of(
            "status", "type", "priority", "projectKey", "labels"
    );
    private static final Set<String> NUMERIC_FIELDS = Set.of(
            "assigneeId", "reporterId", "sprintId", "projectId", "issueId"
    );
    private static final Set<String> TEXT_FIELDS = Set.of(
            "summary", "description"
    );

    // Priority ordinal mapping for range queries
    private static final Map<String, Integer> PRIORITY_ORDER = Map.of(
            "LOWEST", 1, "LOW", 2, "MEDIUM", 3, "HIGH", 4, "HIGHEST", 5
    );

    public Query parse(String jql) {
        if (jql == null || jql.isBlank()) {
            return QueryBuilders.matchAll().build()._toQuery();
        }

        String normalized = jql.trim();
        List<Query> mustQueries = new ArrayList<>();
        List<Query> shouldQueries = new ArrayList<>();

        // Split by OR first (lower precedence)
        String[] orParts = normalized.split("\\s+OR\\s+", -1);

        for (String orPart : orParts) {
            List<Query> andQueries = new ArrayList<>();
            Matcher matcher = CLAUSE_PATTERN.matcher(orPart.trim());

            while (matcher.find()) {
                String field = matcher.group(1).trim();
                String operator = matcher.group(2).trim().toUpperCase();
                String value = matcher.group(3).trim().replaceAll("^\"|\"$", "");

                Query clause = buildClause(field, operator, value);
                if (clause != null) {
                    andQueries.add(clause);
                }
            }

            if (andQueries.size() == 1) {
                shouldQueries.add(andQueries.get(0));
            } else if (andQueries.size() > 1) {
                shouldQueries.add(BoolQuery.of(b -> b.must(andQueries))._toQuery());
            }
        }

        if (shouldQueries.size() == 1) {
            return shouldQueries.get(0);
        } else if (shouldQueries.size() > 1) {
            return BoolQuery.of(b -> b.should(shouldQueries).minimumShouldMatch("1"))._toQuery();
        }

        return QueryBuilders.matchAll().build()._toQuery();
    }

    private Query buildClause(String field, String operator, String value) {
        return switch (operator) {
            case "=" -> buildEqualsQuery(field, value);
            case "!=" -> buildNotEqualsQuery(field, value);
            case ">=" -> buildRangeQuery(field, value, true);
            case "<=" -> buildRangeQuery(field, value, false);
            case "~" -> buildContainsQuery(field, value);
            case "IN" -> buildInQuery(field, value, false);
            case "NOT IN" -> buildInQuery(field, value, true);
            default -> {
                log.warn("Unsupported JQL operator: {}", operator);
                yield null;
            }
        };
    }

    private Query buildEqualsQuery(String field, String value) {
        if (KEYWORD_FIELDS.contains(field)) {
            return TermQuery.of(t -> t.field(field).value(value))._toQuery();
        } else if (NUMERIC_FIELDS.contains(field)) {
            return TermQuery.of(t -> t.field(field).value(Long.parseLong(value)))._toQuery();
        }
        return MatchQuery.of(m -> m.field(field).query(value))._toQuery();
    }

    private Query buildNotEqualsQuery(String field, String value) {
        return BoolQuery.of(b -> b.mustNot(buildEqualsQuery(field, value)))._toQuery();
    }

    private Query buildRangeQuery(String field, String value, boolean gte) {
        if ("priority".equals(field)) {
            int ordinal = PRIORITY_ORDER.getOrDefault(value.toUpperCase(), 0);
            if (gte) {
                return RangeQuery.of(r -> r.number(n -> n.field("priorityOrdinal").gte((double) ordinal)))._toQuery();
            } else {
                return RangeQuery.of(r -> r.number(n -> n.field("priorityOrdinal").lte((double) ordinal)))._toQuery();
            }
        }
        if (gte) {
            return RangeQuery.of(r -> r.number(n -> n.field(field).gte(Double.parseDouble(value))))._toQuery();
        }
        return RangeQuery.of(r -> r.number(n -> n.field(field).lte(Double.parseDouble(value))))._toQuery();
    }

    private Query buildContainsQuery(String field, String value) {
        return MatchQuery.of(m -> m.field(field).query(value).fuzziness("AUTO"))._toQuery();
    }

    private Query buildInQuery(String field, String value, boolean negate) {
        Matcher m = IN_VALUES_PATTERN.matcher(value);
        if (!m.find()) return null;

        List<FieldValue> values = Arrays.stream(m.group(1).split(","))
                .map(v -> v.trim().replaceAll("^\"|\"$", ""))
                .map(FieldValue::of)
                .toList();

        Query termsQuery = TermsQuery.of(t -> t
                .field(field)
                .terms(ts -> ts.value(values)))._toQuery();

        if (negate) {
            return BoolQuery.of(b -> b.mustNot(termsQuery))._toQuery();
        }
        return termsQuery;
    }
}
