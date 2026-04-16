package com.pch.boardreport.controller;

import com.pch.boardreport.domain.BoardCard;
import com.pch.boardreport.dto.BoardCardResponse;
import com.pch.boardreport.dto.MoveCardRequest;
import com.pch.boardreport.dto.SprintBoardResponse;
import com.pch.boardreport.service.BoardService;
import com.pch.common.enums.IssueStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    @GetMapping("/sprints/{sprintId}")
    public ResponseEntity<SprintBoardResponse> getSprintBoard(@PathVariable Long sprintId) {
        Map<IssueStatus, List<BoardCard>> board = boardService.getSprintBoard(sprintId);
        Map<IssueStatus, List<BoardCardResponse>> columns = board.entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> e.getValue().stream().map(BoardCardResponse::from).toList()
                ));
        int totalCards = columns.values().stream().mapToInt(List::size).sum();
        return ResponseEntity.ok(new SprintBoardResponse(sprintId, columns, totalCards));
    }

    @PostMapping("/sprints/{sprintId}/move")
    public ResponseEntity<Void> moveCard(@PathVariable Long sprintId,
                                          @Valid @RequestBody MoveCardRequest request) {
        boardService.moveCard(request.issueKey(), request.newStatus(), request.newOrder());
        return ResponseEntity.ok().build();
    }
}
