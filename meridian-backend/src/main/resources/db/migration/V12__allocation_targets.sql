-- Target allocation: the share of the portfolio's USD value each stock should be. Whatever the
-- targets leave over is the cash target. One row per stock per portfolio.
create table allocation_targets (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    ticker_id bigint not null,
    target_percent decimal(5,2) not null,
    primary key (id),
    unique key uk_allocation_targets_portfolio_ticker (portfolio_id, ticker_id),
    constraint fk_allocation_targets_portfolio foreign key (portfolio_id) references portfolio (id),
    constraint fk_allocation_targets_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;
