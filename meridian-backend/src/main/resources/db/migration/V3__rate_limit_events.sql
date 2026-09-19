-- Failed logins and sign-ups, counted per (limiter, email/IP) so the limits
-- hold across several server instances and survive a restart. `bucket` is a
-- SHA-256 hash, so raw emails and IP addresses are not copied into this table.
create table rate_limit_events (
    id bigint not null auto_increment,
    bucket varchar(64) not null,
    occurred_at datetime(6) not null,
    primary key (id),
    key idx_rate_limit_bucket_time (bucket, occurred_at)
) engine=InnoDB;
