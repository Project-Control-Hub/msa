package com.pch.boardreport.dto;

import com.pch.common.enums.IssueStatus;

import java.time.LocalDate;

public record CfdDataPoint(
    LocalDate date,
    IssueStatus status,
    int count
) {}
