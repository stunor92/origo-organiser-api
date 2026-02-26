# Event Entry Sync API

This API service fetches event entry lists from the origo-eventor-api and saves signed up competitors to the Supabase PostgreSQL database.

## Overview

Based on the `getEventEntryList` functionality from [origo-eventor-api](https://github.com/stunor92/origo-eventor-api), this service provides an endpoint to sync competitor entries from external event systems into the local database.

**Key Differences from eventor-api:**
- Does not return competitors in REST response
- Saves signed up competitors directly to database
- Focuses only on entry list (not result list or start list)

## API Endpoint

### POST `/rest/event/{eventorId}/{eventId}/{raceId}/sync-entries`

**Note:** The `/rest` prefix comes from the application's servlet context-path configured in `application.yml`. The controller is mapped to `/event`, so the full path becomes `/rest/event/...`.

Syncs event entry list from eventor-api and saves signed up competitors to the database.

**Path Parameters:**
- `eventorId` (String) - The eventor organization ID
- `eventId` (String) - The event ID
- `raceId` (String) - The race ID

**Response:**
```json
{
  "message": "Successfully synced competitors",
  "count": 42,
  "eventorId": "1",
  "eventId": "123",
  "raceId": "456"
}
```

**Status Codes:**
- `200 OK` - Competitors synced successfully
- `500 Internal Server Error` - Error syncing competitors

## Configuration

### Environment Variables

Add the following environment variable to configure the eventor-api base URL:

```bash
EVENTOR_API_BASE_URL=https://eventor-api.example.com/rest
```

Or in your deployment platform (e.g., Supabase, Docker):

```yaml
environment:
  EVENTOR_API_BASE_URL: https://eventor-api.example.com/rest
```

**Default:** `http://localhost:8081/rest`

### Database Setup

The database schema is managed by Flyway in a separate Supabase management project.

### Schema Overview

The entry system uses PostgreSQL table inheritance:
- `entry` - Base table with common fields
- `person_entry` - Inherits from `entry`, adds person-specific fields
- `team_entry` - Inherits from `entry`, adds team-specific fields

### Key Tables

**person_entry** - Stores individual competitor entries
- Uses UUID for all IDs (id, race_id, class_id)
- References race and class tables with foreign keys
- Person information: eventor_ref, person_eventor_ref, name, birth year, etc.
- Result data: time, time_behind, position, result_status

**punching_unit_entry** - Stores e-card assignments
- Links entries to their punching units (e-cards)
- Supports both person and team member punching units

**entry_organisation** - Links entries to organizations
- Many-to-many relationship
- Supports multiple organizations per entry

See `db/SCHEMA_REFERENCE.md` for complete schema details.

## Architecture

### Components

1. **EventController** (`controller/EventController.kt`)
   - REST endpoint for syncing event entries
   - Path: `/event/{eventorId}/{eventId}/{raceId}/sync-entries`

2. **EventEntryService** (`services/EventEntryService.kt`)
   - Fetches entry list from eventor-api using RestTemplate
   - Converts entries to PersonCompetitor models
   - Saves competitors to database via repository

3. **CompetitorRepository** (`data/CompetitorRepository.kt`)
   - JDBC-based repository for database operations
   - Supports upsert (insert or update) operations
   - Saves to `person_entry` table with UUID keys
   - Handles punching units in separate table
   - Queries by race ID

4. **Data Models**
   - `PersonEntry` - Entry data from eventor-api
   - `PersonCompetitor` - Competitor data for database storage
   - `EntryStatus` / `CompetitorStatus` - Status enumerations

### Data Flow

```
eventor-api → EventEntryService → CompetitorRepository → PostgreSQL
     ↓              ↓                      ↓
  Entry List   Conversion            Save to DB
```

## Usage Example

### Using cURL

```bash
curl -X POST "http://localhost:8080/rest/event/1/123/456/sync-entries" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Using JavaScript/Fetch

```javascript
const response = await fetch(
  'http://localhost:8080/rest/event/1/123/456/sync-entries',
  {
    method: 'POST',
    headers: {
      'Authorization': 'Bearer YOUR_JWT_TOKEN'
    }
  }
);

const result = await response.json();
console.log(`Synced ${result.count} competitors`);
```

## Database Schema

### person_entry Table (inherits from entry)

The entry system uses PostgreSQL table inheritance. The `person_entry` table inherits common fields from the `entry` base table.

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key, auto-generated |
| race_id | UUID | Foreign key to race table |
| class_id | UUID | Foreign key to class table |
| eventor_ref | TEXT | Entry reference from eventor |
| person_eventor_ref | TEXT | Person identifier from eventor |
| given_name | TEXT | First name |
| family_name | TEXT | Last name |
| birth_year | BIGINT | Birth year |
| nationality | TEXT | Nationality code |
| gender | gender | Gender enum (Man, Woman, Other) |
| bib | TEXT | Bib number |
| status | competitor_status | Competitor status enum |
| start_time | TIMESTAMPTZ | Start time with timezone |
| finish_time | TIMESTAMPTZ | Finish time with timezone |
| time | BIGINT | Result time in seconds |
| time_behind | BIGINT | Time behind leader |
| position | BIGINT | Position in results |
| result_status | result_status | Result status enum |

### Related Tables

**punching_unit_entry** - Stores e-card assignments
- Links entries to punching units via entry_id (UUID)
- Supports leg number for relay teams

**entry_organisation** - Many-to-many relationship
- Links entries to one or more organizations

See `db/SCHEMA_REFERENCE.md` for complete schema details.

## Security

This API endpoint is protected by Spring Security with OAuth2 JWT authentication (Supabase).

Ensure the JWT token has appropriate permissions to access the endpoint.

## Error Handling

- If the eventor-api is unavailable, an empty list is returned and logged
- Individual competitor save errors are logged but don't stop the process
- Transaction rollback occurs if critical errors happen during the sync

## Development

### Build

```bash
./mvnw clean compile
```

### Test

```bash
./mvnw test
```

### Run Locally

```bash
export EVENTOR_API_BASE_URL=http://localhost:8081/rest
export POSTGRES_DB=jdbc:postgresql://localhost:54322/postgres
export POSTGRES_USER=postgres
export POSTGRES_PASSWORD=postgres
./mvnw spring-boot:run
```

## Future Enhancements

Potential improvements:
- Support for team entries (currently only person entries)
- Bulk sync for multiple races
- Incremental updates (only changed entries)
- Webhook support for automatic syncing
- Result list integration (currently excluded per requirements)
