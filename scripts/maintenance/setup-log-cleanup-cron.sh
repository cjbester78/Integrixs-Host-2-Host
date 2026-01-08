#!/bin/bash

# Integrixs Host 2 Host - Setup Log Cleanup Cron Job
# This script sets up a daily cron job to run log cleanup

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

echo -e "${BLUE}Integrixs Host 2 Host - Setup Log Cleanup Cron Job${NC}"
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

# Log cleanup script path
LOG_CLEANUP_SCRIPT="$SCRIPT_DIR/log-cleanup.sh"

if [ ! -f "$LOG_CLEANUP_SCRIPT" ]; then
    print_error "Log cleanup script not found: $LOG_CLEANUP_SCRIPT"
    exit 1
fi

print_status "Setting up cron job for log cleanup..."
print_status "Script location: $LOG_CLEANUP_SCRIPT"

# Cron job entry - runs daily at 2:00 AM
CRON_ENTRY="0 2 * * * $LOG_CLEANUP_SCRIPT >> $PROJECT_ROOT/logs/log-cleanup.log 2>&1"

# Check if cron job already exists
if crontab -l 2>/dev/null | grep -F "$LOG_CLEANUP_SCRIPT" >/dev/null; then
    print_warning "Cron job for log cleanup already exists"
    
    echo ""
    print_status "Current cron jobs containing log cleanup:"
    crontab -l 2>/dev/null | grep -F "$LOG_CLEANUP_SCRIPT" || true
    
    echo ""
    read -p "Do you want to update the existing cron job? (y/N): " -n 1 -r
    echo ""
    
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_status "Keeping existing cron job unchanged"
        exit 0
    fi
    
    # Remove existing cron job
    print_status "Removing existing cron job..."
    (crontab -l 2>/dev/null | grep -v -F "$LOG_CLEANUP_SCRIPT") | crontab -
fi

# Add new cron job
print_status "Adding new cron job..."
print_status "Schedule: Daily at 2:00 AM"
print_status "Command: $LOG_CLEANUP_SCRIPT"
print_status "Log output: $PROJECT_ROOT/logs/log-cleanup.log"

# Create logs directory if it doesn't exist
mkdir -p "$PROJECT_ROOT/logs"

# Add the cron job
(crontab -l 2>/dev/null; echo "$CRON_ENTRY") | crontab -

if [ $? -eq 0 ]; then
    print_success "Cron job added successfully!"
else
    print_error "Failed to add cron job"
    exit 1
fi

echo ""
print_status "Current cron jobs:"
crontab -l

echo ""
echo "=============================================="
print_success "Log cleanup automation setup complete!"
echo ""
print_status "The log cleanup will now run automatically every day at 2:00 AM"
print_status "Cleanup logs will be written to: $PROJECT_ROOT/logs/log-cleanup.log"
print_status "To manually run cleanup: $LOG_CLEANUP_SCRIPT"
print_status "To remove cron job: crontab -e (and delete the line)"
echo "=============================================="