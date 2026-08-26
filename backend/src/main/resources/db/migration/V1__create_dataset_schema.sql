create table dataset_version (
    version varchar(64) primary key,
    source_kind varchar(16) not null
        check (source_kind in ('OBSERVED', 'SYNTHETIC', 'MIXED')),
    generation_seed bigint not null check (generation_seed > 0),
    objective_policy_version varchar(128) not null,
    engine_version varchar(128) not null,
    amount_unit varchar(32) not null check (amount_unit = 'CNY_CENT'),
    published_at timestamptz not null default current_timestamp
);

create table book (
    isbn varchar(13) primary key check (isbn ~ '^[0-9]{13}$'),
    title text not null check (length(btrim(title)) > 0)
);

create table dataset_book (
    dataset_version varchar(64) not null references dataset_version(version),
    isbn varchar(13) not null references book(isbn),
    primary key (dataset_version, isbn)
);

create table platform (
    id varchar(64) primary key,
    observed_name text not null check (length(btrim(observed_name)) > 0),
    public_alias text not null check (length(btrim(public_alias)) > 0)
);

create table platform_rule (
    dataset_version varchar(64) not null references dataset_version(version),
    platform_id varchar(64) not null references platform(id),
    rule_summary text not null check (length(btrim(rule_summary)) > 0),
    threshold jsonb not null check (jsonb_typeof(threshold) = 'object'),
    max_books_per_order integer check (max_books_per_order > 0),
    default_repeat_policy varchar(32) not null
        check (default_repeat_policy in ('ONE_PER_ORDER', 'UP_TO_INVENTORY')),
    multiple_orders_allowed boolean not null,
    primary key (dataset_version, platform_id)
);

create table platform_offer (
    dataset_version varchar(64) not null,
    platform_id varchar(64) not null,
    isbn varchar(13) not null,
    status varchar(16) not null
        check (status in ('ACCEPTED', 'REJECTED', 'UNKNOWN')),
    unit_price_cents bigint not null,
    repeat_policy varchar(32) not null
        check (repeat_policy in ('INHERIT_PLATFORM', 'ONE_PER_ORDER', 'UP_TO_INVENTORY')),
    reason_code varchar(128),
    primary key (dataset_version, platform_id, isbn),
    foreign key (dataset_version, platform_id)
        references platform_rule(dataset_version, platform_id),
    foreign key (dataset_version, isbn)
        references dataset_book(dataset_version, isbn),
    check (
        (status = 'ACCEPTED' and unit_price_cents > 0)
        or (status <> 'ACCEPTED' and unit_price_cents = 0)
    )
);

create index platform_offer_dataset_isbn_idx
    on platform_offer(dataset_version, isbn, platform_id);

create table dataset_disclaimer (
    dataset_version varchar(64) not null references dataset_version(version),
    code varchar(128) not null,
    text text not null check (length(btrim(text)) > 0),
    display_order integer not null check (display_order >= 0),
    primary key (dataset_version, code),
    unique (dataset_version, display_order)
);
