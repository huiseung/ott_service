create table contents (
    id bigint not null auto_increment primary key,
    type varchar(50) not null,
    status varchar(50) not null,
    original_country varchar(2) not null,
    original_language varchar(20) not null,
    release_date date null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    index ix_contents_type_status (type, status),
    index ix_contents_status_created_at (status, created_at, id),
    index ix_contents_original_country (original_country)
) engine = InnoDB;

create table content_localizations (
    id bigint not null auto_increment primary key,
    content_id bigint not null,
    locale varchar(20) not null,
    title varchar(300) not null,
    short_description varchar(500) null,
    description varchar(4000) null,
    created_at datetime(6) not null,
    updated_at datetime(6) not null,
    unique key uk_content_localizations_content_locale (content_id, locale),
    index ix_content_localizations_title (title),
    constraint fk_content_localizations_content foreign key (content_id) references contents(id) on delete cascade
) engine = InnoDB;

create table genres (
    id bigint not null auto_increment primary key,
    code varchar(50) not null,
    status varchar(50) not null,
    sort_order integer not null,
    unique key uk_genres_code (code),
    index ix_genres_status_sort_order (status, sort_order, id)
) engine = InnoDB;

create table content_genres (
    content_id bigint not null,
    genre_id bigint not null,
    primary key (content_id, genre_id),
    index ix_content_genres_genre_content (genre_id, content_id),
    constraint fk_content_genres_content foreign key (content_id) references contents(id) on delete cascade,
    constraint fk_content_genres_genre foreign key (genre_id) references genres(id)
) engine = InnoDB;

create table content_availabilities (
    id bigint not null auto_increment primary key,
    content_id bigint not null,
    country_code varchar(2) not null,
    available_from datetime(6) not null,
    available_until datetime(6) null,
    status varchar(50) not null,
    unique key uk_content_availabilities_content_country (content_id, country_code),
    index ix_content_availabilities_country_status (country_code, status),
    index ix_content_availabilities_window (available_from, available_until),
    constraint fk_content_availabilities_content foreign key (content_id) references contents(id) on delete cascade,
    constraint ck_content_availabilities_window check (available_until is null or available_until >= available_from)
) engine = InnoDB;

insert into genres(code, status, sort_order) values
    ('DRAMA', 'ACTIVE', 10),
    ('ACTION', 'ACTIVE', 20),
    ('THRILLER', 'ACTIVE', 30),
    ('COMEDY', 'ACTIVE', 40),
    ('ROMANCE', 'ACTIVE', 50),
    ('HORROR', 'ACTIVE', 60),
    ('SF', 'ACTIVE', 70),
    ('ANIMATION', 'ACTIVE', 80);
