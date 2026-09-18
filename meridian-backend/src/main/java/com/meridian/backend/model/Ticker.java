package com.meridian.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "tickers")
public class Ticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String symbol;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String exchange;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    public Ticker() {
    }

    public Ticker(String symbol, String name, String exchange) {
        this(symbol, name, exchange, AssetType.STOCK);
    }

    public Ticker(String symbol, String name, String exchange, AssetType assetType) {
        this.symbol = symbol;
        this.name = name;
        this.exchange = exchange;
        this.assetType = assetType;
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }
}
