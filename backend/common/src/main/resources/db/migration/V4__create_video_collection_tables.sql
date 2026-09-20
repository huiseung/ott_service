create table video_collections (
    id bigint not null auto_increment primary key,
    collection_key varchar(120) not null,
    title varchar(200) not null,
    subtitle varchar(500) null,
    collection_type varchar(50) not null,
    reference_value varchar(200) null,
    item_limit integer not null,
    display_order integer not null,
    enabled boolean not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_video_collections_key (collection_key),
    index ix_video_collections_enabled_order (enabled, display_order, id)
) engine = InnoDB;

create table video_collection_items (
    collection_id bigint not null,
    video_id bigint not null,
    sort_order integer not null,
    added_at datetime(6) not null,
    primary key (collection_id, video_id),
    index ix_video_collection_items_order (collection_id, sort_order, video_id),
    constraint fk_video_collection_items_collection foreign key (collection_id) references video_collections(id) on delete cascade,
    constraint fk_video_collection_items_video foreign key (video_id) references videos(id)
) engine = InnoDB;
