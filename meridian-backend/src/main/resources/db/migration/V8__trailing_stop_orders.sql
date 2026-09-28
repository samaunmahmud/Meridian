-- Trailing stop orders: a sell whose stop price follows the market up by a
-- fixed percentage (stored in trail_percent) and never moves down.
alter table orders modify column kind enum('LIMIT','MARKET','STOP_LOSS','TRAILING_STOP') not null;
alter table orders add column trail_percent decimal(6,3);
