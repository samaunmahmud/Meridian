-- The wallet a recurring buy pays from. NULL means USD, as for every
-- recurring order created before this column existed.
alter table recurring_orders add column settlement_currency enum('EUR','GBP','USD');
