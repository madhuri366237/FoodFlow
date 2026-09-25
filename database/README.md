# Database

PostgreSQL 17 runs in Docker (see `../docker-compose.yml`).

From Phase 2, the schema lives in versioned Flyway migrations under `backend/src/main/resources/db/migration/`. Flyway applies them automatically on startup, so every environment gets exactly the same schema. This folder holds helper scripts and notes only.

## Connect with psql (no local install needed)

```bash
docker exec -it foodflow-postgres psql -U foodflow -d foodflow
```

Useful psql commands: `\dt` lists tables, `\d table_name` describes a table, `\q` quits.
