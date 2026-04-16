package com.pch.boardreport.controller;

import com.pch.boardreport.dto.BurndownDataPoint;
import com.pch.boardreport.dto.CfdDataPoint;
import com.pch.boardreport.dto.VelocityDataPoint;
import com.pch.boardreport.service.BurndownService;
import com.pch.boardreport.service.CfdService;
import com.pch.boardreport.service.VelocityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/charts")
@RequiredArgsConstructor
public class ChartController {

    private final BurndownService burndownService;
    private final VelocityService velocityService;
    private final CfdService cfdService;

    @GetMapping("/burndown/{sprintId}")
    public ResponseEntity<List<BurndownDataPoint>> getBurndown(@PathVariable Long sprintId) {
        List<BurndownDataPoint> data = burndownService.getBurndown(sprintId).stream()
                .map(BurndownDataPoint::from)
                .toList();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/velocity/{projectId}")
    public ResponseEntity<List<VelocityDataPoint>> getVelocity(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "5") int sprintCount) {
        List<VelocityDataPoint> data = velocityService.getVelocity(projectId, sprintCount).stream()
                .map(VelocityDataPoint::from)
                .toList();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/cfd/{projectId}")
    public ResponseEntity<List<CfdDataPoint>> getCfd(
            @PathVariable Long projectId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(cfdService.getCfd(projectId, from, to));
    }
}
