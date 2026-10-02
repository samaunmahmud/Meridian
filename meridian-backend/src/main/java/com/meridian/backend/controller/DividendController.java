package com.meridian.backend.controller;

import com.meridian.backend.dto.DividendInfoResponse;
import com.meridian.backend.model.User;
import com.meridian.backend.service.DividendService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DividendController {

    private final DividendService dividendService;

    public DividendController(DividendService dividendService) {
        this.dividendService = dividendService;
    }

    @GetMapping("/dividends/{symbol}")
    public DividendInfoResponse getDividends(@PathVariable String symbol, @AuthenticationPrincipal User user) {
        return dividendService.getInfo(user, symbol);
    }
}
