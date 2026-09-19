-- Money held back by open limit orders that pay from a non-USD wallet, so it
-- cannot be spent twice (USD already does this with portfolio.reserved_cash).
-- The default only fills existing rows; new rows always set the value.
alter table wallets add column reserved_balance decimal(14,4) not null default 0;
alter table wallets alter column reserved_balance drop default;
