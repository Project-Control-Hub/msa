package com.pch.search.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.List;

/**
 * Elasticsearch 이슈 인덱스 문서.
 * Issue Service 이벤트 수신 시 실시간 동기화.
 */
@Document(indexName = "pch-issues")
@Setting(settingPath = "/elasticsearch/issue-settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDocument {

    @Id
    private String issueKey;

    @Field(type = FieldType.Long)
    private Long issueId;

    @Field(type = FieldType.Long)
    private Long projectId;

    @Field(type = FieldType.Keyword)
    private String projectKey;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "nori_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String type;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String priority;

    @Field(type = FieldType.Long)
    private Long sprintId;

    @Field(type = FieldType.Long)
    private Long assigneeId;

    @Field(type = FieldType.Long)
    private Long reporterId;

    @Field(type = FieldType.Keyword)
    private List<String> labels;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant createdAt;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Instant updatedAt;

    @Field(type = FieldType.Text, analyzer = "ngram_analyzer", searchAnalyzer = "standard")
    private String summaryAutocomplete;
}
