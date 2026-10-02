-- Your own note on a stock and the price you would like to buy it at.
alter table watchlist_items add column note varchar(500);
alter table watchlist_items add column target_price decimal(12,4);
