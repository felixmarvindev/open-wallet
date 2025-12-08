#!/bin/bash
set -e

# Configure PostgreSQL to allow connections from host
echo "host    all             all             0.0.0.0/0               scram-sha-256" >> /var/lib/postgresql/data/pgdata/pg_hba.conf
echo "host    all             all             ::/0                    scram-sha-256" >> /var/lib/postgresql/data/pgdata/pg_hba.conf

# Reload PostgreSQL configuration
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    SELECT pg_reload_conf();
EOSQL

echo "PostgreSQL configured to accept external connections"

