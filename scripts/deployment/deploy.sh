#!/bin/bash

# H2H File Transfer - Full Deploy Script
# This script performs a complete deployment including database migrations

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

echo -e "${BLUE}H2H File Transfer - Full Deployment${NC}"
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

# Check if we're in the correct directory
if [ ! -f "$PROJECT_ROOT/pom.xml" ]; then
    print_error "Not in H2H project root directory. Please run from project root."
    exit 1
fi

print_status "Starting full deployment..."
print_status "Project root: $PROJECT_ROOT"

# Stop existing application
print_status "Stopping existing application..."
if "$SCRIPT_DIR/../application/stopapp.sh"; then
    print_success "Application stopped successfully"
else
    print_warning "Could not stop application (may not be running)"
fi

# Run database migrations
print_status "Running database migrations..."
if "$SCRIPT_DIR/../database/migrate.sh"; then
    print_success "Database migrations completed"
else
    print_error "Database migration failed"
    exit 1
fi

# Clean and build everything
print_status "Cleaning previous builds..."
cd "$PROJECT_ROOT"
mvn clean

# Build frontend
print_status "Building React frontend..."
cd "$PROJECT_ROOT/frontend"

print_status "Installing/updating frontend dependencies..."
npm install

print_status "Running frontend linting..."
npm run lint || print_warning "Linting warnings found"

print_status "Building frontend production bundle..."
npm run build

if [ $? -eq 0 ]; then
    print_success "Frontend build completed"
else
    print_error "Frontend build failed"
    exit 1
fi

# Sync frontend to backend resources efficiently
print_status "Syncing frontend to backend resources..."
BACKEND_STATIC="$PROJECT_ROOT/backend/src/main/resources/static"
mkdir -p "$BACKEND_STATIC"

# Use rsync for efficient sync (only copies changed files)
if command -v rsync >/dev/null 2>&1; then
    print_status "Using rsync for efficient file sync..."
    rsync -av --delete dist/ "$BACKEND_STATIC/"
    print_success "Frontend files synced efficiently with rsync"
else
    # Fallback: Always copy for full deploy to ensure consistency
    print_status "rsync not available, performing full copy for deployment..."
    rm -rf "$BACKEND_STATIC"/*
    cp -r dist/* "$BACKEND_STATIC/"
    print_success "Frontend files copied to backend"
fi

# Build backend with tests
print_status "Building backend application with tests..."
cd "$PROJECT_ROOT"

print_status "Running Maven install with tests..."
mvn install

if [ $? -eq 0 ]; then
    print_success "Backend build and tests completed"
else
    print_error "Backend build or tests failed"
    exit 1
fi

# Create deployment artifact
print_status "Creating deployment artifact..."
DEPLOY_DIR="$PROJECT_ROOT/target/deployment"
mkdir -p "$DEPLOY_DIR"

cp "$PROJECT_ROOT/backend/target/backend-1.0.0-SNAPSHOT.jar" "$DEPLOY_DIR/h2h-file-transfer.jar"
cp "$PROJECT_ROOT/backend/src/main/resources/application.yml" "$DEPLOY_DIR/"
print_success "Deployment artifact created in target/deployment/"

# Start application
print_status "Starting H2H File Transfer application..."
if "$SCRIPT_DIR/../application/startapp.sh"; then
    print_success "Application started successfully"
    
    # Wait a moment for startup
    print_status "Waiting for application startup..."
    sleep 5
    
    # Test health endpoint
    print_status "Testing application health..."
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        print_success "Application health check passed"
    else
        print_warning "Application may still be starting up"
    fi
    
    echo ""
    echo "=============================================="
    print_success "Full deployment completed successfully!"
    echo -e "${GREEN}Application is available at: http://localhost:8080${NC}"
    echo -e "${GREEN}Login with: Administrator / Int3grix@01${NC}"
    echo -e "${BLUE}Deployment artifact: target/deployment/h2h-file-transfer.jar${NC}"
    echo "=============================================="
else
    print_error "Failed to start application"
    exit 1
fi