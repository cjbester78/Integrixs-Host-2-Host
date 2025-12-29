#!/bin/bash

# H2H File Transfer - Restart Application Script

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

APP_NAME="H2H File Transfer"

echo -e "${BLUE}$APP_NAME - Restarting Application${NC}"
echo "=============================================="

# Function to print status
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Stop the application
print_status "Stopping application..."
if "$SCRIPT_DIR/stopapp.sh"; then
    print_success "Application stopped successfully"
else
    print_error "Failed to stop application"
    exit 1
fi

# Wait a moment
print_status "Waiting 3 seconds before restart..."
sleep 3

# Start the application
print_status "Starting application..."
if "$SCRIPT_DIR/startapp.sh"; then
    print_success "Application restarted successfully"
    echo ""
    echo "=============================================="
    print_success "$APP_NAME restarted!"
    echo -e "${GREEN}Application is available at: http://localhost:8080${NC}"
    echo -e "${GREEN}Login with: Administrator / Int3grix@01${NC}"
    echo "=============================================="
else
    print_error "Failed to start application"
    exit 1
fi