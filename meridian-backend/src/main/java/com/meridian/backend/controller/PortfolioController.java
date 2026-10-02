package com.meridian.backend.controller;

import com.meridian.backend.dto.BenchmarkResponse;
import com.meridian.backend.dto.DepositRequest;
import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderNoteRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.PerformanceResponse;
import com.meridian.backend.dto.PortfolioResponse;
import com.meridian.backend.dto.PortfolioSnapshotResponse;
import com.meridian.backend.dto.ReplaceOrderRequest;
import com.meridian.backend.dto.TransactionResponse;
import com.meridian.backend.dto.WithdrawRequest;
import com.meridian.backend.model.User;
import com.meridian.backend.service.BenchmarkService;
import com.meridian.backend.service.PerformanceService;
import com.meridian.backend.service.PortfolioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PerformanceService performanceService;
    private final BenchmarkService benchmarkService;

    public PortfolioController(PortfolioService portfolioService, PerformanceService performanceService,
                               BenchmarkService benchmarkService) {
        this.portfolioService = portfolioService;
        this.performanceService = performanceService;
        this.benchmarkService = benchmarkService;
    }

    @GetMapping("/portfolio")
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal User user) {
        return portfolioService.getPortfolioValuation(user);
    }

    @GetMapping("/portfolio/history")
    public List<PortfolioSnapshotResponse> getPortfolioHistory(@AuthenticationPrincipal User user,
                                                               @RequestParam(required = false) Integer points) {
        return portfolioService.getPortfolioHistory(user, points);
    }

    @GetMapping("/portfolio/performance")
    public PerformanceResponse getPerformance(@AuthenticationPrincipal User user) {
        return performanceService.getPerformance(user);
    }

    @GetMapping("/portfolio/benchmark")
    public BenchmarkResponse getBenchmark(@AuthenticationPrincipal User user, @RequestParam String symbol,
                                          @RequestParam(required = false) String range,
                                          @RequestParam(required = false) Integer points) {
        return benchmarkService.compare(user, symbol, range, points);
    }

    @PostMapping("/portfolio/deposit")
    public TransactionResponse deposit(@RequestBody DepositRequest request, @AuthenticationPrincipal User user) {
        return portfolioService.deposit(request.amount(), user);
    }

    @PostMapping("/portfolio/withdraw")
    public TransactionResponse withdraw(@RequestBody WithdrawRequest request, @AuthenticationPrincipal User user) {
        return portfolioService.withdraw(request.amount(), user);
    }

    @GetMapping("/portfolio/transactions")
    public List<TransactionResponse> getTransactions(@AuthenticationPrincipal User user) {
        return portfolioService.getTransactionHistory(user);
    }

    @PostMapping("/orders")
    public OrderResponse placeOrder(@RequestBody OrderRequest request, @AuthenticationPrincipal User user) {
        return portfolioService.placeOrder(request, user);
    }

    @GetMapping("/orders")
    public List<OrderResponse> getOrders(@AuthenticationPrincipal User user) {
        return portfolioService.getOrderHistory(user);
    }

    @GetMapping("/orders/open")
    public List<OrderResponse> getOpenOrders(@AuthenticationPrincipal User user) {
        return portfolioService.getOpenOrders(user);
    }

    // Replaces a pending order with one on the new terms; the response is the new order.
    @PutMapping("/orders/{id}")
    public OrderResponse replaceOrder(@PathVariable Long id, @RequestBody ReplaceOrderRequest request,
                                      @AuthenticationPrincipal User user) {
        return portfolioService.replaceOrder(id, request, user);
    }

    @PutMapping("/orders/{id}/note")
    public OrderResponse setNote(@PathVariable Long id, @RequestBody OrderNoteRequest request,
                                 @AuthenticationPrincipal User user) {
        return portfolioService.setNote(id, request.note(), user);
    }

    @DeleteMapping("/orders/{id}")
    public void cancelOrder(@PathVariable Long id, @AuthenticationPrincipal User user) {
        portfolioService.cancelOrder(id, user);
    }
}
