create table content_images (
    id bigint not null auto_increment primary key,
    content_id bigint not null,
    image_type varchar(50) not null,
    object_key varchar(512) not null,
    width integer not null,
    height integer not null,
    file_size bigint not null,
    mime_type varchar(100) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_content_images_content_type (content_id, image_type),
    unique key uk_content_images_object_key (object_key),
    constraint fk_content_images_content foreign key (content_id) references contents(id),
    constraint ck_content_images_dimensions check (width > 0 and height > 0),
    constraint ck_content_images_file_size check (file_size > 0)
) engine = InnoDB;

create table episode_images (
    id bigint not null auto_increment primary key,
    episode_id bigint not null,
    image_type varchar(50) not null,
    object_key varchar(512) not null,
    width integer not null,
    height integer not null,
    file_size bigint not null,
    mime_type varchar(100) not null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_episode_images_episode_type (episode_id, image_type),
    unique key uk_episode_images_object_key (object_key),
    constraint fk_episode_images_episode foreign key (episode_id) references episodes(id),
    constraint ck_episode_images_dimensions check (width > 0 and height > 0),
    constraint ck_episode_images_file_size check (file_size > 0)
) engine = InnoDB;
