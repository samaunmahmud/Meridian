-- Cash dividends. `dividends` is what a stock pays (from the market data provider);
-- `dividend_payments` is what each portfolio was paid, one row per portfolio and
-- dividend, so a dividend can never be paid to the same account twice.
alter table transactions modify column type enum('BUY','CONVERSION','DEPOSIT','DIVIDEND','FEE','SELL','WITHDRAWAL') not null;

-- When the provider was last asked for this ticker's dividends.
alter table tickers add column dividends_checked_at datetime(6);

create table dividends (
    id bigint not null auto_increment,
    ticker_id bigint not null,
    ex_date date not null,
    pay_date date not null,
    amount decimal(12,6) not null,
    paid_out_at datetime(6),
    primary key (id),
    unique key uk_dividends_ticker_ex_date (ticker_id, ex_date),
    constraint fk_dividends_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table dividend_payments (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    dividend_id bigint not null,
    shares decimal(14,4) not null,
    amount decimal(14,4) not null,
    paid_at datetime(6) not null,
    primary key (id),
    unique key uk_dividend_payments_portfolio_dividend (portfolio_id, dividend_id),
    constraint fk_dividend_payments_portfolio foreign key (portfolio_id) references portfolio (id),
    constraint fk_dividend_payments_dividend foreign key (dividend_id) references dividends (id)
) engine=InnoDB;
