package com.pch.boardreport.controller;

import com.pch.boardreport.dto.CreateGadgetRequest;
import com.pch.boardreport.dto.GadgetResponse;
import com.pch.boardreport.service.DashboardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboards")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/{projectId}")
    public ResponseEntity<List<GadgetResponse>> getGadgets(
            @PathVariable Long projectId,
            @RequestParam Long userId) {
        List<GadgetResponse> gadgets = dashboardService.getGadgets(projectId, userId).stream()
                .map(GadgetResponse::from)
                .toList();
        return ResponseEntity.ok(gadgets);
    }

    @PostMapping("/{projectId}/gadgets")
    public ResponseEntity<GadgetResponse> addGadget(
            @PathVariable Long projectId,
            @RequestParam Long userId,
            @Valid @RequestBody CreateGadgetRequest request) {
        var gadget = dashboardService.addGadget(projectId, userId,
                request.gadgetType(), request.position(), request.config());
        return ResponseEntity.ok(GadgetResponse.from(gadget));
    }

    @DeleteMapping("/gadgets/{gadgetId}")
    public ResponseEntity<Void> removeGadget(@PathVariable Long gadgetId) {
        dashboardService.removeGadget(gadgetId);
        return ResponseEntity.ok().build();
    }
}
