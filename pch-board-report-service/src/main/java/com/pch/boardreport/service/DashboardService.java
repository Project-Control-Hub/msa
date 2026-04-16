package com.pch.boardreport.service;

import com.pch.boardreport.domain.DashboardGadget;
import com.pch.boardreport.domain.GadgetType;
import com.pch.boardreport.repository.DashboardGadgetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardGadgetRepository gadgetRepository;

    @Transactional(readOnly = true)
    public List<DashboardGadget> getGadgets(Long projectId, Long userId) {
        return gadgetRepository.findByProjectIdAndUserIdOrderByPositionAsc(projectId, userId);
    }

    @Transactional
    public DashboardGadget addGadget(Long projectId, Long userId, GadgetType gadgetType, int position, String config) {
        DashboardGadget gadget = DashboardGadget.builder()
                .projectId(projectId)
                .userId(userId)
                .gadgetType(gadgetType)
                .position(position)
                .config(config)
                .build();
        return gadgetRepository.save(gadget);
    }

    @Transactional
    public void removeGadget(Long gadgetId) {
        gadgetRepository.deleteById(gadgetId);
    }

    @Transactional
    public void updatePosition(Long gadgetId, int position) {
        gadgetRepository.findById(gadgetId)
                .ifPresent(gadget -> gadget.setPosition(position));
    }
}
