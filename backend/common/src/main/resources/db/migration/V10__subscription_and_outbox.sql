create table user_subscriptions (
    user_id bigint not null primary key,
    subscription_id char(36) not null,
    status varchar(20) not null,
    first_activated_at datetime(6) null,
    activated_at datetime(6) null,
    cancelled_at datetime(6) null,
    created_at datetime(6) not null,
    unique key uk_subscription_id (subscription_id),
    constraint fk_subscription_user foreign key (user_id) references users(id),
    constraint ck_subscription_status check (status in ('PENDING','ACTIVE','CANCELLED'))
) engine=InnoDB;

create table subscription_checkouts (
    checkout_id char(36) not null primary key,
    request_id char(36) not null,
    user_id bigint not null,
    subscription_id char(36) not null,
    content_id bigint null,
    cta_event_id char(36) null,
    status varchar(20) not null,
    created_at datetime(6) not null,
    expires_at datetime(6) not null,
    activated_at datetime(6) null,
    unique key uk_checkout_request (user_id, request_id),
    index ix_checkout_user (user_id, created_at),
    constraint fk_checkout_user foreign key (user_id) references users(id),
    constraint fk_checkout_subscription foreign key (subscription_id) references user_subscriptions(subscription_id),
    constraint fk_checkout_content foreign key (content_id) references contents(id),
    constraint ck_checkout_status check (status in ('CREATED','ACTIVATED'))
) engine=InnoDB;

create table subscription_outbox (
    id bigint not null auto_increment primary key,
    event_id char(36) not null,
    aggregate_id char(36) not null,
    event_type varchar(64) not null,
    payload json not null,
    status varchar(20) not null default 'PENDING',
    attempts int not null default 0,
    available_at datetime(6) not null,
    claim_token char(36) null,
    created_at datetime(6) not null,
    sent_at datetime(6) null,
    last_error_code varchar(100) null,
    unique key uk_subscription_outbox_event (event_id),
    index ix_subscription_outbox_poll (status, available_at, id),
    index ix_subscription_outbox_order (aggregate_id, id, status),
    constraint ck_outbox_status check (status in ('PENDING','PROCESSING','SENT','FAILED'))
) engine=InnoDB;
