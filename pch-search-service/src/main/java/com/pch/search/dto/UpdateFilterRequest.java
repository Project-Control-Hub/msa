package com.pch.search.dto;

public record UpdateFilterRequest(
        String name,
        String jqlExpression
) {}
