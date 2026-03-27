#!/bin/bash
# CS6650 Assignment 3 - PostgreSQL 15 Setup Script
# Target: Amazon Linux 2023 EC2 (t2.medium, 4GB RAM)
# Usage: DB_PASSWORD=<password> ./setup-postgresql.sh

set -e

if [ -z "$DB_PASSWORD" ]; then
  echo "ERROR: DB_PASSWORD environment variable must be set"
  echo "Usage: DB_PASSWORD=yourpassword ./setup-postgresql.sh"
  exit 1
fi

echo "=== Installing PostgreSQL 15 ==="
sudo dnf install -y postgresql15 postgresql15-server

echo "=== Initializing database cluster ==="
sudo postgresql-setup --initdb

echo "=== Tuning postgresql.conf for t2.medium (4GB RAM) ==="
PG_CONF="/var/lib/pgsql/data/postgresql.conf"
sudo sed -i "s/^#shared_buffers.*/shared_buffers = 1GB/" $PG_CONF
sudo sed -i "s/^#effective_cache_size.*/effective_cache_size = 2GB/" $PG_CONF
sudo sed -i "s/^#work_mem.*/work_mem = 16MB/" $PG_CONF
sudo sed -i "s/^#maintenance_work_mem.*/maintenance_work_mem = 256MB/" $PG_CONF
sudo sed -i "s/^#max_connections.*/max_connections = 200/" $PG_CONF
# Enable pg_stat_statements for monitoring
sudo sed -i "s/^#shared_preload_libraries.*/shared_preload_libraries = 'pg_stat_statements'/" $PG_CONF
# Allow connections from anywhere (restricted at pg_hba level)
sudo sed -i "s/^#listen_addresses.*/listen_addresses = '*'/" $PG_CONF

echo "=== Configuring pg_hba.conf ==="
PG_HBA="/var/lib/pgsql/data/pg_hba.conf"
# Back up original
sudo cp $PG_HBA ${PG_HBA}.bak
# Write new config
sudo tee $PG_HBA > /dev/null <<EOF
# TYPE  DATABASE    USER        ADDRESS           METHOD
local   all         postgres                      peer
local   all         all                           md5
host    all         all         127.0.0.1/32      md5
host    all         all         ::1/128           md5
# VPC internal (adjust CIDR to match your VPC)
host    all         all         10.0.0.0/16       md5
EOF

echo "=== Starting PostgreSQL ==="
sudo systemctl enable postgresql
sudo systemctl start postgresql

echo "=== Creating database and user ==="
sudo -u postgres psql -c "CREATE USER chatuser WITH PASSWORD '$DB_PASSWORD';"
sudo -u postgres psql -c "CREATE DATABASE chatdb OWNER chatuser;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE chatdb TO chatuser;"
# Allow chatuser to create objects in public schema
sudo -u postgres psql -d chatdb -c "GRANT ALL ON SCHEMA public TO chatuser;"
# Enable pg_stat_statements extension
sudo -u postgres psql -d chatdb -c "CREATE EXTENSION IF NOT EXISTS pg_stat_statements;"

echo "=== Applying schema ==="
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/schema.sql" ]; then
  PGPASSWORD=$DB_PASSWORD psql -h localhost -U chatuser -d chatdb -f "$SCRIPT_DIR/schema.sql"
  echo "Schema applied successfully."
else
  echo "WARNING: schema.sql not found at $SCRIPT_DIR/schema.sql — skipping."
fi

echo ""
echo "=== Setup Complete ==="
echo "Connection string: postgresql://chatuser:***@localhost:5432/chatdb"
echo "Test connection:   PGPASSWORD=$DB_PASSWORD psql -h localhost -U chatuser -d chatdb -c 'SELECT version();'"
