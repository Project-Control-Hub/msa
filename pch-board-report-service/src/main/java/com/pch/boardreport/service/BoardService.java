package com.pch.boardreport.service;

import com.pch.boardreport.domain.BoardCard;
import com.pch.boardreport.repository.BoardCardRepository;
import com.pch.common.enums.IssueStatus;
import com.pch.common.event.IssueCreatedEvent;
import com.pch.common.event.IssueDeletedEvent;
import com.pch.common.event.IssueStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {

    private final BoardCardRepository boardCardRepository;

    @Cacheable(value = "sprint-board", key = "#sprintId")
    @Transactional(readOnly = true)
    public Map<IssueStatus, List<BoardCard>> getSprintBoard(Long sprintId) {
        List<BoardCard> cards = boardCardRepository.findBySprintIdOrderByCardOrderAsc(sprintId);
        return cards.stream().collect(Collectors.groupingBy(BoardCard::getStatus));
    }

    @CacheEvict(value = "sprint-board", key = "#issueKey.substring(0, #issueKey.indexOf('-'))")
    @Transactional
    public void moveCard(String issueKey, IssueStatus newStatus, Integer newOrder) {
        boardCardRepository.findByIssueId(parseIssueId(issueKey))
            .ifPresent(card -> {
                card.setStatus(newStatus);
                if (newOrder != null) {
                    card.setCardOrder(newOrder);
                }
            });
    }

    @CacheEvict(value = "sprint-board", key = "#event.projectId", allEntries = true)
    @Transactional
    public void syncBoardCard(IssueCreatedEvent event) {
        BoardCard card = BoardCard.builder()
                .issueId(event.getIssueId())
                .issueKey(event.getIssueKey())
                .summary("") // summary not available in event — will sync via API later
                .status(event.getStatus())
                .priority(null) // not in event
                .type(event.getIssueType())
                .assigneeId(event.getAssigneeId())
                .sprintId(null)
                .projectId(event.getProjectId())
                .cardOrder(0)
                .build();
        boardCardRepository.save(card);
        log.info("BoardCard created for issue: {}", event.getIssueKey());
    }

    @CacheEvict(value = "sprint-board", allEntries = true)
    @Transactional
    public void syncBoardCard(IssueStatusChangedEvent event) {
        boardCardRepository.findByIssueId(event.getIssueId())
            .ifPresent(card -> {
                card.setStatus(event.getToStatus());
                log.info("BoardCard status updated: {} → {}", event.getIssueKey(), event.getToStatus());
            });
    }

    @CacheEvict(value = "sprint-board", allEntries = true)
    @Transactional
    public void removeBoardCard(IssueDeletedEvent event) {
        boardCardRepository.deleteByIssueId(event.getIssueId());
        log.info("BoardCard removed for issue: {}", event.getIssueKey());
    }

    private Long parseIssueId(String issueKey) {
        // In production, would look up by issueKey
        return boardCardRepository.findAll().stream()
                .filter(c -> c.getIssueKey().equals(issueKey))
                .map(BoardCard::getIssueId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Card not found: " + issueKey));
    }
}
