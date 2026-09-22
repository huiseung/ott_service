create table collections (
    id bigint not null auto_increment primary key,
    status varchar(50) not null,
    min_visible_items integer not null,
    version bigint not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    index ix_collections_status_created_at (status, created_at, id),
    constraint ck_collections_min_visible_items check (min_visible_items >= 0)
) engine = InnoDB;

create table collection_localizations (
    id bigint not null auto_increment primary key,
    collection_id bigint not null,
    locale varchar(20) not null,
    title varchar(300) not null,
    description varchar(4000) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_collection_localizations_collection_locale (collection_id, locale),
    index ix_collection_localizations_title (title),
    constraint fk_collection_localizations_collection foreign key (collection_id) references collections(id) on delete cascade
) engine = InnoDB;

create table collection_items (
    id bigint not null auto_increment primary key,
    collection_id bigint not null,
    content_id bigint not null,
    display_order integer not null,
    created_at datetime(6) not null,
    unique key uk_collection_items_collection_content (collection_id, content_id),
    unique key uk_collection_items_collection_order (collection_id, display_order),
    index ix_collection_items_content (content_id),
    constraint fk_collection_items_collection foreign key (collection_id) references collections(id) on delete cascade,
    constraint fk_collection_items_content foreign key (content_id) references contents(id),
    constraint ck_collection_items_display_order check (display_order >= 1)
) engine = InnoDB;

create table collection_availabilities (
    id bigint not null auto_increment primary key,
    collection_id bigint not null,
    country_code varchar(2) not null,
    available_from datetime(6) not null,
    available_until datetime(6) null,
    status varchar(50) not null,
    unique key uk_collection_availabilities_collection_country (collection_id, country_code),
    index ix_collection_availabilities_country_status (country_code, status),
    constraint fk_collection_availabilities_collection foreign key (collection_id) references collections(id) on delete cascade,
    constraint ck_collection_availabilities_window check (available_until is null or available_until >= available_from)
) engine = InnoDB;
