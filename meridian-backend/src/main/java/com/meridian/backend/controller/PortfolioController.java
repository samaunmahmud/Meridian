package com.meridian.backend.controller;

import com.meridian.backend.dto.DepositRequest;
import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.PortfolioResponse;
import com.meridian.backend.dto.PortfolioSnapshotResponse;
import com.meridian.backend.dto.TransactionResponse;
import com.meridian.backend.dto.WithdrawRequest;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/portfolio")
    public PortfolioResponse getPortfolio(@AuthenticationPrincipal User user) {
        return portfolioService.getPortfolioValuation(user);
    }

    @GetMapping("/portfolio/history")
    public List<PortfolioSnapshotResponse> getPortfolioHistory(@AuthenticationPrincipal User user) {
        return portfolioService.getPortfolioHistory(user);
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

    @DeleteMapping("/orders/{id}")
    public void cancelOrder(@PathVariable Long id, @AuthenticationPrincipal User user) {
        portfolioService.cancelOrder(id, user);
    }
}
