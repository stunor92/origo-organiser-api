# Database Schema Reference

This directory contains documentation about the database schema used by this API.

## Schema Management

The database schema is managed by Flyway in a separate Supabase management project.
This API consumes the schema but does not create or migrate it.

## Schema Files

- `SCHEMA_REFERENCE.md` - Documentation of the entry tables and related structures

## Entry System

The API saves competitor entries to these tables:
- `person_entry` - Individual competitor entries (inherits from `entry`)
- `team_entry` - Team entries for relay events (inherits from `entry`)
- `punching_unit_entry` - E-card assignments
- `entry_organisation` - Organization associations
- `split_time` - Split times for entries
- `entry_fee` - Fee associations

See `SCHEMA_REFERENCE.md` for detailed table structures.
