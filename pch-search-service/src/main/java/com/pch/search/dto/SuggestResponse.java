package com.pch.search.dto;

import java.util.List;

public record SuggestResponse(
        List<String> suggestions
) {}
