package com.pch.search.service;

import com.pch.search.domain.SavedFilter;
import com.pch.search.dto.CreateFilterRequest;
import com.pch.search.dto.FilterResponse;
import com.pch.search.repository.SavedFilterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SavedFilterServiceTest {

    @InjectMocks private SavedFilterService filterService;
    @Mock private SavedFilterRepository filterRepository;

    @Test
    @DisplayName("필터 생성 성공")
    void createFilter() {
        when(filterRepository.save(any(SavedFilter.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateFilterRequest req = new CreateFilterRequest("My Filter", "status = OPEN");
        FilterResponse response = filterService.create(req, 1L);

        assertNotNull(response);
        assertEquals("My Filter", response.name());
        assertEquals("status = OPEN", response.jqlExpression());
    }

    @Test
    @DisplayName("다른 사용자의 필터 수정 시 403 Forbidden")
    void updateOtherUserFilter() {
        SavedFilter filter = SavedFilter.create(1L, "Filter", "status = OPEN");
        when(filterRepository.findById(1L)).thenReturn(Optional.of(filter));

        assertThrows(ResponseStatusException.class,
                () -> filterService.update(1L, "New Name", null, 999L));
    }
}
