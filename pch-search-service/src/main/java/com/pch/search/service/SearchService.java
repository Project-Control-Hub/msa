package com.pch.search.service;

import com.pch.search.domain.IssueDocument;
import com.pch.search.dto.SearchResponse;
import com.pch.search.repository.IssueDocumentRepository;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final IssueDocumentRepository issueDocumentRepository;
    private final JqlParser jqlParser;

    public Page<SearchResponse> search(String jql, Pageable pageable) {
        Query query = jqlParser.parse(jql);

        NativeQuery searchQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(pageable)
                .build();

        SearchHits<IssueDocument> hits = elasticsearchOperations.search(
                searchQuery, IssueDocument.class);

        List<SearchResponse> results = hits.getSearchHits().stream()
                .map(hit -> SearchResponse.from(hit.getContent()))
                .toList();

        return new PageImpl<>(results, pageable, hits.getTotalHits());
    }

    public List<String> suggest(String keyword, int limit) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.match(m -> m
                        .field("summaryAutocomplete")
                        .query(keyword)))
                .withMaxResults(limit)
                .build();

        SearchHits<IssueDocument> hits = elasticsearchOperations.search(
                query, IssueDocument.class);

        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent().getSummary())
                .distinct()
                .toList();
    }

    public void indexIssue(IssueDocument document) {
        document.setSummaryAutocomplete(document.getSummary());
        issueDocumentRepository.save(document);
        log.info("[ES Indexed] issueKey={}", document.getIssueKey());
    }

    public void updateIssueStatus(String issueKey, String newStatus) {
        issueDocumentRepository.findById(issueKey).ifPresent(doc -> {
            doc.setStatus(newStatus);
            issueDocumentRepository.save(doc);
            log.info("[ES Updated] issueKey={}, newStatus={}", issueKey, newStatus);
        });
    }

    public void removeIssue(String issueKey) {
        issueDocumentRepository.deleteById(issueKey);
        log.info("[ES Removed] issueKey={}", issueKey);
    }

    public long reindexAll() {
        // TODO: Issue Service Internal API 호출 → 전체 이슈 재색인
        log.info("[ES Reindex] Full reindex triggered");
        return issueDocumentRepository.count();
    }
}
