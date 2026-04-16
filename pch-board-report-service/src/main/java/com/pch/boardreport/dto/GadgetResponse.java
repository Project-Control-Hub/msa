package com.pch.boardreport.dto;

import com.pch.boardreport.domain.DashboardGadget;
import com.pch.boardreport.domain.GadgetType;

public record GadgetResponse(
    Long id,
    GadgetType gadgetType,
    int position,
    String config
) {
    public static GadgetResponse from(DashboardGadget g) {
        return new GadgetResponse(g.getId(), g.getGadgetType(), g.getPosition(), g.getConfig());
    }
}
