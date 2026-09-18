package com.meridian.backend.controller;

import com.meridian.backend.dto.AlertRequest;
import com.meridian.backend.dto.AlertResponse;
import com.meridian.backend.model.User;
import com.meridian.backend.service.AlertService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping
    public AlertResponse createAlert(@RequestBody AlertRequest request, @AuthenticationPrincipal User user) {
        return alertService.createAlert(request, user);
    }

    @GetMapping
    public List<AlertResponse> getAlerts(@AuthenticationPrincipal User user) {
        return alertService.getAlerts(user);
    }

    @DeleteMapping("/{id}")
    public void deleteAlert(@PathVariable Long id, @AuthenticationPrincipal User user) {
        alertService.deleteAlert(id, user);
    }
}
