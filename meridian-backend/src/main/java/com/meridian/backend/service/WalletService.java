package com.meridian.backend.service;

import com.meridian.backend.dto.ConvertRequest;
import com.meridian.backend.dto.ConvertResponse;
import com.meridian.backend.dto.WalletResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Transaction;
import com.meridian.backend.model.TransactionType;
import com.meridian.backend.model.User;
import com.meridian.backend.model.Wallet;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.TransactionRepository;
import com.meridian.backend.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class WalletService {

    // Fintech-style markup applied on top of the raw FX rate on every
    // conversion — this is where currency-exchange revenue actually comes
    // from in a real app like Revolut, not a separate fee line.
    private static final BigDecimal FX_SPREAD = new BigDecimal("0.005");

    private static final BigDecimal STARTING_CASH = new BigDecimal("10000.00");

    private final PortfolioRepository portfolioRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final FxRateService fxRateService;

    public WalletService(PortfolioRepository portfolioRepository,
                          WalletRepository walletRepository,
                          TransactionRepository transactionRepository,
                          FxRateService fxRateService) {
        this.portfolioRepository = portfolioRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.fxRateService = fxRateService;
    }

    private Portfolio getOrCreatePortfolio(User user) {
        return portfolioRepository.findByUserId(user.getId())
                .orElseGet(() -> portfolioRepository.save(new Portfolio(user, STARTING_CASH)));
    }

    private Wallet getOrCreateWallet(Portfolio portfolio, SupportedCurrency currency) {
        return walletRepository.findByPortfolioIdAndCurrency(portfolio.getId(), currency)
                .orElseGet(() -> new Wallet(portfolio, currency, BigDecimal.ZERO));
    }

    // USD lives on Portfolio.cashBalance already; every other currency gets
    // its balance reported alongside it here so the frontend can render one
    // unified list of accounts.
    public List<WalletResponse> getWallets(User user) {
        Portfolio portfolio = getOrCreatePortfolio(user);
        List<WalletResponse> wallets = new ArrayList<>();
        wallets.add(new WalletResponse(SupportedCurrency.USD, portfolio.getCashBalance()));

        List<Wallet> stored = walletRepository.findByPortfolioId(portfolio.getId());
        for (SupportedCurrency currency : SupportedCurrency.values()) {
            if (currency == SupportedCurrency.USD) continue;
            BigDecimal balance = stored.stream()
                    .filter(w -> w.getCurrency() == currency)
                    .findFirst()
                    .map(Wallet::getBalance)
                    .orElse(BigDecimal.ZERO);
            wallets.add(new WalletResponse(currency, balance));
        }
        return wallets;
    }

    private BigDecimal balanceOf(Portfolio portfolio, SupportedCurrency currency) {
        return currency == SupportedCurrency.USD
                ? portfolio.getCashBalance()
                : getOrCreateWallet(portfolio, currency).getBalance();
    }

    private void setBalance(Portfolio portfolio, SupportedCurrency currency, BigDecimal newBalance) {
        if (currency == SupportedCurrency.USD) {
            portfolio.setCashBalance(newBalance);
            portfolioRepository.save(portfolio);
        } else {
            Wallet wallet = getOrCreateWallet(portfolio, currency);
            wallet.setBalance(newBalance);
            walletRepository.save(wallet);
        }
    }

    @Transactional
    public ConvertResponse convert(ConvertRequest request, User user) {
        if (request.fromCurrency() == null || request.toCurrency() == null) {
            throw new InvalidRequestException("Both fromCurrency and toCurrency are required");
        }
        if (request.fromCurrency() == request.toCurrency()) {
            throw new InvalidRequestException("Cannot convert a currency into itself");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }

        Portfolio portfolio = getOrCreatePortfolio(user);
        BigDecimal available = balanceOf(portfolio, request.fromCurrency());
        if (available.compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient " + request.fromCurrency() + " balance: need " + request.amount()
                            + " but only " + available + " available");
        }

        BigDecimal rawRate = fxRateService.getRate(request.fromCurrency(), request.toCurrency());
        BigDecimal appliedRate = rawRate.multiply(BigDecimal.ONE.subtract(FX_SPREAD)).setScale(8, RoundingMode.HALF_UP);
        BigDecimal amountCredited = request.amount().multiply(appliedRate).setScale(4, RoundingMode.HALF_UP);
        BigDecimal fee = request.amount().multiply(rawRate).setScale(4, RoundingMode.HALF_UP).subtract(amountCredited);

        BigDecimal newFromBalance = available.subtract(request.amount());
        setBalance(portfolio, request.fromCurrency(), newFromBalance);
        transactionRepository.save(new Transaction(
                portfolio, TransactionType.CONVERSION, request.amount().negate(), newFromBalance,
                request.fromCurrency().name(), "Converted to " + request.toCurrency(), null));

        BigDecimal newToBalance = balanceOf(portfolio, request.toCurrency()).add(amountCredited);
        setBalance(portfolio, request.toCurrency(), newToBalance);
        transactionRepository.save(new Transaction(
                portfolio, TransactionType.CONVERSION, amountCredited, newToBalance,
                request.toCurrency().name(), "Converted from " + request.fromCurrency(), null));

        return new ConvertResponse(request.fromCurrency(), request.toCurrency(), request.amount(), amountCredited, appliedRate, fee);
    }

    @Transactional
    public WalletResponse deposit(SupportedCurrency currency, BigDecimal amount, User user) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Deposit amount must be greater than zero");
        }

        Portfolio portfolio = getOrCreatePortfolio(user);
        BigDecimal newBalance = balanceOf(portfolio, currency).add(amount);
        setBalance(portfolio, currency, newBalance);

        transactionRepository.save(new Transaction(portfolio, TransactionType.DEPOSIT, amount, newBalance,
                currency.name(), "Deposit", null));
        return new WalletResponse(currency, newBalance);
    }

    @Transactional
    public WalletResponse withdraw(SupportedCurrency currency, BigDecimal amount, User user) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Withdrawal amount must be greater than zero");
        }

        Portfolio portfolio = getOrCreatePortfolio(user);
        BigDecimal available = balanceOf(portfolio, currency);
        if (available.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient " + currency + " balance: need " + amount + " but only " + available + " available");
        }

        BigDecimal newBalance = available.subtract(amount);
        setBalance(portfolio, currency, newBalance);

        transactionRepository.save(new Transaction(portfolio, TransactionType.WITHDRAWAL, amount.negate(), newBalance,
                currency.name(), "Withdrawal", null));
        return new WalletResponse(currency, newBalance);
    }
}
