-- Alerts set as a percentage move ("tell me if it falls 5% from here"). The
-- target price is worked out when the alert is created, so these columns are
-- only there to show the alert the way it was asked for.
alter table alerts add column move_percent decimal(6,2);
alter table alerts add column reference_price decimal(12,4);
