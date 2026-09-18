package com.meridian.backend.controller;

import com.meridian.backend.dto.ConvertRequest;
import com.meridian.backend.dto.ConvertResponse;
import com.meridian.backend.dto.FxRateResponse;
import com.meridian.backend.dto.WalletAmountRequest;
import com.meridian.backend.dto.WalletResponse;
import com.meridian.backend.model.User;
import com.meridian.backend.service.FxRateService;
import com.meridian.backend.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WalletController {

    private final WalletService walletService;
    private final FxRateService fxRateService;

    public WalletController(WalletService walletService, FxRateService fxRateService) {
        this.walletService = walletService;
        this.fxRateService = fxRateService;
    }

    @GetMapping("/wallets")
    public List<WalletResponse> getWallets(@AuthenticationPrincipal User user) {
        return walletService.getWallets(user);
    }

    @PostMapping("/wallets/convert")
    public ConvertResponse convert(@RequestBody ConvertRequest request, @AuthenticationPrincipal User user) {
        return walletService.convert(request, user);
    }

    @PostMapping("/wallets/deposit")
    public WalletResponse deposit(@RequestBody WalletAmountRequest request, @AuthenticationPrincipal User user) {
        return walletService.deposit(request.currency(), request.amount(), user);
    }

    @PostMapping("/wallets/withdraw")
    public WalletResponse withdraw(@RequestBody WalletAmountRequest request, @AuthenticationPrincipal User user) {
        return walletService.withdraw(request.currency(), request.amount(), user);
    }

    @GetMapping("/fx-rates")
    public List<FxRateResponse> getFxRates() {
        return fxRateService.getAllRates();
    }
}
