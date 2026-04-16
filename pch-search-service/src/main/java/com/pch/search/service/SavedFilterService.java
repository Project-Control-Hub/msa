package com.pch.search.service;

import com.pch.search.domain.SavedFilter;
import com.pch.search.dto.CreateFilterRequest;
import com.pch.search.dto.FilterResponse;
import com.pch.search.repository.SavedFilterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SavedFilterService {

    private final SavedFilterRepository filterRepository;

    @Transactional
    public FilterResponse create(CreateFilterRequest req, Long userId) {
        SavedFilter filter = SavedFilter.create(userId, req.name(), req.jqlExpression());
        return FilterResponse.from(filterRepository.save(filter));
    }

    public List<FilterResponse> getByUser(Long userId) {
        return filterRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(FilterResponse::from)
                .toList();
    }

    @Transactional
    public FilterResponse update(Long filterId, String name, String jqlExpression, Long userId) {
        SavedFilter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!filter.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        filter.update(name, jqlExpression);
        return FilterResponse.from(filter);
    }

    @Transactional
    public void delete(Long filterId, Long userId) {
        SavedFilter filter = filterRepository.findById(filterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!filter.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        filterRepository.delete(filter);
    }
}
