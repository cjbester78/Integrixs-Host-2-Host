#!/bin/bash

# Integrixs Host 2 Host - Log Cleanup Script
# This script manages log file retention according to the policy:
# - Keep uncompressed logs for 7 days in logs/ directory
# - Compress logs older than 7 days to logs/backup/ directory
# - Delete compressed logs older than 30 days from logs/backup/ directory

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory and project root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Log directories
LOGS_DIR="$PROJECT_ROOT/logs"
BACKUP_DIR="$PROJECT_ROOT/logs/backup"

echo -e "${BLUE}Integrixs Host 2 Host - Log Cleanup${NC}"
echo "=============================================="

# Function to print status
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if logs directory exists
if [ ! -d "$LOGS_DIR" ]; then
    print_error "Logs directory not found: $LOGS_DIR"
    exit 1
fi

print_status "Log cleanup started at $(date)"
print_status "Logs directory: $LOGS_DIR"
print_status "Backup directory: $BACKUP_DIR"

# Create backup directory if it doesn't exist
mkdir -p "$BACKUP_DIR"

# Current date for calculations
CURRENT_DATE=$(date +%s)
SEVEN_DAYS_AGO=$((CURRENT_DATE - 7 * 24 * 3600))
THIRTY_DAYS_AGO=$((CURRENT_DATE - 30 * 24 * 3600))

print_status "Current date: $(date)"
print_status "Archiving files older than: $(date -r $SEVEN_DAYS_AGO)"
print_status "Deleting files older than: $(date -r $THIRTY_DAYS_AGO)"

echo ""

# STEP 1: Archive logs older than 7 days (compress and move to backup)
print_status "Step 1: Archiving logs older than 7 days..."

ARCHIVED_COUNT=0
ARCHIVE_ERRORS=0

# Find log files older than 7 days (exclude already compressed files and directories)
find "$LOGS_DIR" -maxdepth 1 -name "*.log" -type f -mtime +7 | while read -r logfile; do
    if [ -f "$logfile" ]; then
        filename=$(basename "$logfile")
        backup_filename="${filename%.log}.gz"
        backup_path="$BACKUP_DIR/$backup_filename"
        
        print_status "Archiving: $filename"
        
        # Compress the file to backup directory
        if gzip -c "$logfile" > "$backup_path"; then
            # Verify the compressed file was created successfully
            if [ -f "$backup_path" ] && [ -s "$backup_path" ]; then
                # Remove the original file
                rm "$logfile"
                print_success "Archived: $filename -> $backup_filename"
                ARCHIVED_COUNT=$((ARCHIVED_COUNT + 1))
            else
                print_error "Failed to create compressed file: $backup_filename"
                ARCHIVE_ERRORS=$((ARCHIVE_ERRORS + 1))
                # Remove the incomplete compressed file
                rm -f "$backup_path"
            fi
        else
            print_error "Failed to compress: $filename"
            ARCHIVE_ERRORS=$((ARCHIVE_ERRORS + 1))
        fi
    fi
done

# Count archived files (need to do this outside the while loop due to subshell)
ARCHIVED_COUNT=$(find "$LOGS_DIR" -maxdepth 1 -name "*.log" -type f -mtime +7 2>/dev/null | wc -l | tr -d ' ')

if [ "$ARCHIVED_COUNT" -eq 0 ]; then
    print_status "No log files found that need archiving"
else
    print_success "Archived $ARCHIVED_COUNT log files to backup directory"
fi

echo ""

# STEP 2: Delete compressed backup files older than 30 days
print_status "Step 2: Deleting backup files older than 30 days..."

DELETED_COUNT=0
DELETE_ERRORS=0

# Find compressed files older than 30 days
find "$BACKUP_DIR" -name "*.gz" -type f -mtime +30 | while read -r gzfile; do
    if [ -f "$gzfile" ]; then
        filename=$(basename "$gzfile")
        
        print_status "Deleting old backup: $filename"
        
        if rm "$gzfile"; then
            print_success "Deleted: $filename"
            DELETED_COUNT=$((DELETED_COUNT + 1))
        else
            print_error "Failed to delete: $filename"
            DELETE_ERRORS=$((DELETE_ERRORS + 1))
        fi
    fi
done

# Count deleted files
DELETED_COUNT=$(find "$BACKUP_DIR" -name "*.gz" -type f -mtime +30 2>/dev/null | wc -l | tr -d ' ')

if [ "$DELETED_COUNT" -eq 0 ]; then
    print_status "No backup files found that need deletion"
else
    print_success "Deleted $DELETED_COUNT old backup files"
fi

echo ""

# STEP 3: Summary and disk usage
print_status "Step 3: Summary and disk usage..."

# Count current files
CURRENT_LOGS=$(find "$LOGS_DIR" -maxdepth 1 -name "*.log" -type f | wc -l | tr -d ' ')
BACKUP_FILES=$(find "$BACKUP_DIR" -name "*.gz" -type f | wc -l | tr -d ' ')

print_status "Current log files: $CURRENT_LOGS"
print_status "Backup files: $BACKUP_FILES"

# Calculate disk usage
if command -v du >/dev/null 2>&1; then
    LOGS_SIZE=$(du -sh "$LOGS_DIR" 2>/dev/null | cut -f1 || echo "Unknown")
    BACKUP_SIZE=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "Unknown")
    
    print_status "Logs directory size: $LOGS_SIZE"
    print_status "Backup directory size: $BACKUP_SIZE"
fi

# Show recent log files
print_status "Recent log files (last 10):"
find "$LOGS_DIR" -maxdepth 1 -name "*.log" -type f -printf "%T@ %Tc %f\n" 2>/dev/null | sort -nr | head -10 | while read timestamp date file; do
    echo "  $file ($date)"
done

echo ""
echo "=============================================="
print_success "Log cleanup completed at $(date)"

if [ "$ARCHIVE_ERRORS" -gt 0 ] || [ "$DELETE_ERRORS" -gt 0 ]; then
    print_warning "Completed with errors: $ARCHIVE_ERRORS archive errors, $DELETE_ERRORS delete errors"
    exit 1
else
    print_success "All operations completed successfully"
    exit 0
fi