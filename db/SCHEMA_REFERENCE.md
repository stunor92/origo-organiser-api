# Database Schema Reference

The database schema is managed by Flyway in a separate Supabase management project.

## Entry Tables

The entry system uses PostgreSQL table inheritance for person and team entries.

### Base Table: entry
```sql
create table entry
(
    id    uuid primary key default gen_random_uuid() unique,
    race_id     uuid              not null,
    class_id    uuid              not null,
    bib         text,
    status      competitor_status not null,
    start_time  timestamptz,
    finish_time timestamptz,
    time            bigint,
    time_behind     bigint,
    position        bigint,
    result_status   result_status,
    foreign key (class_id) references class (id) on update cascade on delete cascade,
    foreign key (race_id) references race (id) on update cascade on delete cascade
);
```

### Person Entry (inherits from entry)
```sql
create table person_entry
(
    eventor_ref text,
    person_eventor_ref text,
    given_name    text   not null,
    family_name   text   not null,
    birth_year    bigint,
    nationality   text,
    gender        gender
) inherits (entry);
```

### Team Entry (inherits from entry)
```sql
create table team_entry
(
    name text not null
) inherits (entry);
```

## Related Tables

### entry_organisation
Links entries to organizations (many-to-many).

### team_member
Stores individual team members for relay teams.

### entry_fee
Links entries to fees applied.

### split_time
Stores split times for entries.

### punching_unit_entry
Links entries to punching units (e-cards).

## Note

This API saves entries fetched from eventor-api into these tables.
The actual schema is managed in the Supabase Flyway migration project.
