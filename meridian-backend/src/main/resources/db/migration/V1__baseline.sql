-- Baseline: the schema exactly as Hibernate's ddl-auto=update built it up to
-- the point Flyway was introduced. A brand-new database runs this file; a
-- database that already has these tables is marked as being at version 1
-- (spring.flyway.baseline-on-migrate) and skips it.

create table users (
    id bigint not null auto_increment,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_users_email (email)
) engine=InnoDB;

create table tickers (
    id bigint not null auto_increment,
    symbol varchar(255) not null,
    name varchar(255) not null,
    exchange varchar(255) not null,
    asset_type enum('CRYPTO','STOCK') not null,
    primary key (id),
    unique key uk_tickers_symbol (symbol)
) engine=InnoDB;

create table portfolio (
    id bigint not null auto_increment,
    user_id bigint not null,
    cash_balance decimal(14,4) not null,
    reserved_cash decimal(14,4) not null,
    primary key (id),
    unique key uk_portfolio_user (user_id),
    constraint fk_portfolio_user foreign key (user_id) references users (id)
) engine=InnoDB;

create table holdings (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    ticker_id bigint not null,
    quantity decimal(14,4) not null,
    reserved_quantity decimal(14,4) not null,
    avg_cost decimal(14,4) not null,
    primary key (id),
    unique key uk_holdings_portfolio_ticker (portfolio_id, ticker_id),
    constraint fk_holdings_portfolio foreign key (portfolio_id) references portfolio (id),
    constraint fk_holdings_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table orders (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    ticker_id bigint not null,
    type enum('BUY','SELL') not null,
    kind enum('LIMIT','MARKET','STOP_LOSS') not null,
    status enum('CANCELLED','FILLED','PENDING','REJECTED') not null,
    quantity decimal(14,4) not null,
    price decimal(14,4),
    limit_price decimal(14,4),
    stop_price decimal(14,4),
    fee_amount decimal(14,4),
    reserved_amount decimal(14,4),
    rejection_reason varchar(255),
    settlement_currency enum('EUR','GBP','USD'),
    settlement_amount decimal(14,4),
    created_at datetime(6) not null,
    executed_at datetime(6),
    cancelled_at datetime(6),
    primary key (id),
    constraint fk_orders_portfolio foreign key (portfolio_id) references portfolio (id),
    constraint fk_orders_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table transactions (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    type enum('BUY','CONVERSION','DEPOSIT','FEE','SELL','WITHDRAWAL') not null,
    amount decimal(14,4) not null,
    balance_after decimal(14,4) not null,
    created_at datetime(6) not null,
    currency varchar(255),
    description varchar(255),
    related_order_id bigint,
    primary key (id),
    constraint fk_transactions_portfolio foreign key (portfolio_id) references portfolio (id)
) engine=InnoDB;

create table portfolio_snapshots (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    total_value decimal(14,4) not null,
    recorded_at datetime(6) not null,
    primary key (id),
    key idx_portfolio_recorded_at (portfolio_id, recorded_at),
    constraint fk_snapshots_portfolio foreign key (portfolio_id) references portfolio (id)
) engine=InnoDB;

create table watchlist_items (
    id bigint not null auto_increment,
    user_id bigint not null,
    ticker_id bigint not null,
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_watchlist_user_ticker (user_id, ticker_id),
    constraint fk_watchlist_user foreign key (user_id) references users (id),
    constraint fk_watchlist_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table alerts (
    id bigint not null auto_increment,
    user_id bigint not null,
    ticker_id bigint not null,
    direction enum('ABOVE','BELOW') not null,
    target_price decimal(12,4) not null,
    triggered bit not null,
    created_at datetime(6) not null,
    triggered_at datetime(6),
    primary key (id),
    constraint fk_alerts_user foreign key (user_id) references users (id),
    constraint fk_alerts_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table price_history (
    id bigint not null auto_increment,
    ticker_id bigint not null,
    price decimal(12,4) not null,
    recorded_at datetime(6) not null,
    primary key (id),
    key idx_ticker_recorded_at (ticker_id, recorded_at),
    constraint fk_price_history_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table recurring_orders (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    ticker_id bigint not null,
    amount decimal(14,4) not null,
    frequency enum('DAILY','MONTHLY','WEEKLY') not null,
    next_run_at datetime(6) not null,
    active bit not null,
    created_at datetime(6) not null,
    primary key (id),
    constraint fk_recurring_portfolio foreign key (portfolio_id) references portfolio (id),
    constraint fk_recurring_ticker foreign key (ticker_id) references tickers (id)
) engine=InnoDB;

create table fx_rates (
    id bigint not null auto_increment,
    base_currency enum('EUR','GBP','USD') not null,
    quote_currency enum('EUR','GBP','USD') not null,
    rate decimal(18,8) not null,
    updated_at datetime(6) not null,
    primary key (id),
    unique key uk_fx_rates_pair (base_currency, quote_currency)
) engine=InnoDB;

create table wallets (
    id bigint not null auto_increment,
    portfolio_id bigint not null,
    currency enum('EUR','GBP','USD') not null,
    balance decimal(14,4) not null,
    primary key (id),
    unique key uk_wallets_portfolio_currency (portfolio_id, currency),
    constraint fk_wallets_portfolio foreign key (portfolio_id) references portfolio (id)
) engine=InnoDB;
