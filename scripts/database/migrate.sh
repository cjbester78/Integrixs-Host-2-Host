#!/bin/bash

# Database Migration Script for H2H File Transfer
# This script runs migrations independently of the Spring Boot application
# to avoid startup hanging issues with Flyway

set -e  # Exit on any error

# Configuration
DB_URL="jdbc:postgresql://localhost:5432/h2h_dev"
DB_USER="h2h_user"
DB_PASSWORD="h2h_dev_password"
DB_SCHEMA="public"
# Script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MIGRATION_DIR="$PROJECT_ROOT/backend/src/main/resources/db/migration"

echo "======================================"
echo "H2H File Transfer - Database Migration"
echo "======================================"
echo "Database: $DB_URL"
echo "Schema: $DB_SCHEMA"
echo "Migration Directory: $MIGRATION_DIR"
echo "======================================"

# Check if database is accessible
echo "Testing database connection..."
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -c "SELECT version();" > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✓ Database connection successful"
else
    echo "✗ Database connection failed"
    echo "Please ensure PostgreSQL is running and credentials are correct"
    exit 1
fi

# Check if migration directory exists
if [ ! -d "$MIGRATION_DIR" ]; then
    echo "✗ Migration directory not found: $MIGRATION_DIR"
    exit 1
fi

echo "✓ Migration directory found"

# Count migration files
MIGRATION_COUNT=$(ls -1 "$MIGRATION_DIR"/V*.sql 2>/dev/null | wc -l | tr -d ' ')
echo "Found $MIGRATION_COUNT migration files"

# Show current migration status
echo ""
echo "Current migration status:"
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -c "SELECT installed_rank, version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;" 2>/dev/null || echo "No migration history found"

echo ""
echo "Running migrations..."

# Create flyway_schema_history table if it doesn't exist
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -c "
CREATE TABLE IF NOT EXISTS flyway_schema_history (
    installed_rank INTEGER NOT NULL,
    version VARCHAR(50),
    description VARCHAR(200) NOT NULL,
    type VARCHAR(20) NOT NULL,
    script VARCHAR(1000) NOT NULL,
    checksum INTEGER,
    installed_by VARCHAR(100) NOT NULL,
    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    execution_time INTEGER NOT NULL,
    success BOOLEAN NOT NULL,
    CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
);" 2>/dev/null || true

# Get the latest migration version from database (only numeric versions)
LATEST_VERSION=$(PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -t -c "
    SELECT COALESCE(MAX(CAST(version AS INTEGER)), 0) 
    FROM flyway_schema_history 
    WHERE version ~ '^[0-9]+$';" 2>/dev/null | tr -d ' ')

if [ -z "$LATEST_VERSION" ] || [ "$LATEST_VERSION" = "" ]; then
    LATEST_VERSION=0
fi

echo "Latest migration version in database: V$LATEST_VERSION"

# Find and run pending migrations
for migration_file in "$MIGRATION_DIR"/V*.sql; do
    if [ -f "$migration_file" ]; then
        # Extract version number from filename (e.g., V001 from V001__description.sql)
        filename=$(basename "$migration_file")
        version=$(echo "$filename" | sed -E 's/^V([0-9]+)__.*/\1/')
        version_num=$(echo "$version" | sed 's/^0*//')  # Remove leading zeros
        
        # Skip if version extraction failed
        if [[ ! "$version_num" =~ ^[0-9]+$ ]]; then
            echo "Skipping invalid migration filename: $filename"
            continue
        fi
        
        if [ "$version_num" -gt "$LATEST_VERSION" ]; then
            echo "Running migration: $filename"
            description=$(echo "$filename" | sed 's/V[0-9]\+__\(.*\)\.sql/\1/' | tr '_' ' ')
            
            # Execute the migration
            echo "  Executing SQL from $migration_file..."
            if PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -f "$migration_file" > /tmp/migration_output.log 2>&1; then
                # Record the migration in flyway_schema_history
                PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -c "
                INSERT INTO flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) 
                VALUES (
                    (SELECT COALESCE(MAX(installed_rank), 0) + 1 FROM flyway_schema_history),
                    '$version',
                    '$description',
                    'SQL',
                    '$filename',
                    1234567890,
                    '$DB_USER',
                    CURRENT_TIMESTAMP,
                    1,
                    true
                );"
                echo "  ✓ Migration $filename completed successfully"
            else
                echo "  ✗ Migration $filename failed"
                echo "Error output:"
                cat /tmp/migration_output.log
                exit 1
            fi
        else
            echo "Skipping already applied migration: $filename (version $version_num <= $LATEST_VERSION)"
        fi
    fi
done

echo ""
echo "✓ Migrations completed successfully!"

# Show final migration status
echo ""
echo "Final migration status:"
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -c "SELECT installed_rank, version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;"

# Show created tables
echo ""
echo "Database tables:"
PGPASSWORD="$DB_PASSWORD" psql -h localhost -p 5432 -U "$DB_USER" -d h2h_dev -c "\d"

echo ""
echo "======================================"
echo "Database migration completed successfully!"
echo "You can now start the application."
echo "======================================"