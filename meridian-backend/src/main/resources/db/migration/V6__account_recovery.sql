-- Email verification, password reset, and ending old sessions after a password change.

-- Existing accounts have never verified their address, so they start unverified.
-- (The default only fills existing rows; new rows always set the value.)
alter table users add column email_verified bit not null default 0;
alter table users alter column email_verified drop default;

-- When set, sessions issued before this moment are no longer accepted.
alter table users add column password_changed_at datetime(6);

-- One-time links sent by email. Only a SHA-256 hash of the token is stored, so a
-- copy of this table cannot be used to take over an account.
create table auth_tokens (
    id bigint not null auto_increment,
    user_id bigint not null,
    purpose enum('EMAIL_VERIFICATION','PASSWORD_RESET') not null,
    token_hash varchar(64) not null,
    expires_at datetime(6) not null,
    used_at datetime(6),
    created_at datetime(6) not null,
    primary key (id),
    unique key uk_auth_tokens_hash (token_hash),
    constraint fk_auth_tokens_user foreign key (user_id) references users (id)
) engine=InnoDB;
