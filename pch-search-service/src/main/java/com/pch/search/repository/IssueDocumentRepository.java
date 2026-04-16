package com.pch.search.repository;

import com.pch.search.domain.IssueDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface IssueDocumentRepository extends ElasticsearchRepository<IssueDocument, String> {

    List<IssueDocument> findByProjectId(Long projectId);

    void deleteByIssueKey(String issueKey);
}
