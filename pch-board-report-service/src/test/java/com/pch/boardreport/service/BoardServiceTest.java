package com.pch.boardreport.service;

import com.pch.boardreport.domain.BoardCard;
import com.pch.boardreport.repository.BoardCardRepository;
import com.pch.common.enums.IssueStatus;
import com.pch.common.enums.IssueType;
import com.pch.common.event.IssueCreatedEvent;
import com.pch.common.event.IssueStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock private BoardCardRepository boardCardRepository;
    @InjectMocks private BoardService boardService;

    @Test
    void getSprintBoard_groupsByStatus() {
        BoardCard card1 = BoardCard.builder().issueKey("PRJ-1").status(IssueStatus.OPEN).build();
        BoardCard card2 = BoardCard.builder().issueKey("PRJ-2").status(IssueStatus.DONE).build();
        BoardCard card3 = BoardCard.builder().issueKey("PRJ-3").status(IssueStatus.OPEN).build();
        when(boardCardRepository.findBySprintIdOrderByCardOrderAsc(1L)).thenReturn(List.of(card1, card2, card3));

        Map<IssueStatus, List<BoardCard>> board = boardService.getSprintBoard(1L);

        assertThat(board.get(IssueStatus.OPEN)).hasSize(2);
        assertThat(board.get(IssueStatus.DONE)).hasSize(1);
    }

    @Test
    void syncBoardCard_createsFromIssueCreatedEvent() {
        IssueCreatedEvent event = new IssueCreatedEvent(
            100L, "PRJ-100", 1L, IssueType.STORY, IssueStatus.OPEN, 10L, "issue-service");

        boardService.syncBoardCard(event);

        ArgumentCaptor<BoardCard> captor = ArgumentCaptor.forClass(BoardCard.class);
        verify(boardCardRepository).save(captor.capture());
        assertThat(captor.getValue().getIssueKey()).isEqualTo("PRJ-100");
        assertThat(captor.getValue().getStatus()).isEqualTo(IssueStatus.OPEN);
    }

    @Test
    void syncBoardCard_updatesStatusOnStatusChangedEvent() {
        BoardCard card = BoardCard.builder().issueId(100L).issueKey("PRJ-100").status(IssueStatus.OPEN).build();
        when(boardCardRepository.findByIssueId(100L)).thenReturn(Optional.of(card));

        IssueStatusChangedEvent event = new IssueStatusChangedEvent(
            100L, "PRJ-100", 1L, 1L, IssueStatus.OPEN, IssueStatus.IN_PROGRESS, 10L, "issue-service");

        boardService.syncBoardCard(event);

        assertThat(card.getStatus()).isEqualTo(IssueStatus.IN_PROGRESS);
    }
}
