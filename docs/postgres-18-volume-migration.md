# PostgreSQL 18 volume and migration notes

## Why the Compose mount changed

PostgreSQL 18's [official image](https://github.com/docker-library/docs/blob/master/postgres/README.md#pgdata) stores its default data directory at `/var/lib/postgresql/18/docker` and declares `/var/lib/postgresql` as its volume. Compose therefore mounts the named volume at `/var/lib/postgresql`. The isolated Compose verification checks that mount and confirms that data survives container replacement and restart.

## Existing PostgreSQL 17 data

Changing an image tag or mount path does not upgrade a PostgreSQL data directory. A volume initialized by PostgreSQL 17 contains data in the older layout. Starting PostgreSQL 18 against it may stop with an old-data warning; it must not be treated as an empty database or discarded.

Before using the PostgreSQL 18 Compose image with existing data:

1. Stop the stack without deleting its volume. Do not use `docker compose down --volumes`.
2. Make and verify a backup of the existing database.
3. Perform a deliberate major-version migration, such as a tested logical dump and restore or a supported `pg_upgrade` procedure with both server versions available.
4. Verify payment and idempotency records, application migrations, and a restart using the new volume layout.
5. Retain the original volume until the migrated database has been verified and the backup-retention decision is explicit.

This repository's disposable verification creates a unique Compose project and volume. It validates PostgreSQL 18 behavior; it does not migrate a developer's existing volume.
