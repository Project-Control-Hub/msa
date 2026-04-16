package com.pch.boardreport.dto;

import com.pch.boardreport.domain.GadgetType;
import jakarta.validation.constraints.NotNull;

public record CreateGadgetRequest(
    @NotNull GadgetType gadgetType,
    int position,
    String config
) {}
