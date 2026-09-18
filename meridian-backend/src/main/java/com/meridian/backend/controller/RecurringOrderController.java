package com.meridian.backend.controller;

import com.meridian.backend.dto.RecurringOrderRequest;
import com.meridian.backend.dto.RecurringOrderResponse;
import com.meridian.backend.model.User;
import com.meridian.backend.service.RecurringOrderService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurring-orders")
public class RecurringOrderController {

    private final RecurringOrderService recurringOrderService;

    public RecurringOrderController(RecurringOrderService recurringOrderService) {
        this.recurringOrderService = recurringOrderService;
    }

    @GetMapping
    public List<RecurringOrderResponse> getRecurringOrders(@AuthenticationPrincipal User user) {
        return recurringOrderService.getRecurringOrders(user);
    }

    @PostMapping
    public RecurringOrderResponse create(@RequestBody RecurringOrderRequest request, @AuthenticationPrincipal User user) {
        return recurringOrderService.create(request, user);
    }

    @DeleteMapping("/{id}")
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal User user) {
        recurringOrderService.cancel(id, user);
    }
}
