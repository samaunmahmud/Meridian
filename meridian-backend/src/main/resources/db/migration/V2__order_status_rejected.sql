-- OrderStatus.REJECTED was added after some databases already existed, and
-- Hibernate's ddl-auto=update never changes an existing column, so those
-- databases have orders.status as ENUM('CANCELLED','FILLED','PENDING') and
-- refuse to store a rejected order. Re-stating the column is a no-op on a
-- database that already has all four values (including one created by V1).
-- This replaces the old SchemaUpgrades startup hack.
alter table orders modify column status enum('CANCELLED','FILLED','PENDING','REJECTED') not null;
