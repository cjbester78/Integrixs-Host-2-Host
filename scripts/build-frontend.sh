#!/bin/bash

# H2H File Transfer - Frontend Build Script
# This script builds just the React frontend and copies it to backend

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory and paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_ROOT="$SCRIPT_DIR/../frontend"
BACKEND_ROOT="$SCRIPT_DIR/.."

echo -e "${BLUE}H2H File Transfer - Frontend Build${NC}"
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

# Check if frontend directory exists
if [ ! -d "$FRONTEND_ROOT" ]; then
    print_error "Frontend directory not found: $FRONTEND_ROOT"
    exit 1
fi

# Check if backend directory exists
if [ ! -d "$BACKEND_ROOT" ]; then
    print_error "Backend directory not found: $BACKEND_ROOT"
    exit 1
fi

print_status "Frontend root: $FRONTEND_ROOT"
print_status "Backend root: $BACKEND_ROOT"

# Build frontend
print_status "Building React frontend..."
cd "$FRONTEND_ROOT"

# Check if package.json exists
if [ ! -f "package.json" ]; then
    print_error "package.json not found in frontend directory"
    exit 1
fi

# Install dependencies if node_modules doesn't exist
if [ ! -d "node_modules" ]; then
    print_status "Installing frontend dependencies..."
    npm install
fi

print_status "Building frontend production bundle..."
npm run build

if [ $? -eq 0 ]; then
    print_success "Frontend build completed"
else
    print_error "Frontend build failed"
    exit 1
fi

# Copy frontend to backend resources
print_status "Copying frontend to backend resources..."
BACKEND_STATIC="$BACKEND_ROOT/backend/src/main/resources/static"
mkdir -p "$BACKEND_STATIC"
rm -rf "$BACKEND_STATIC"/*

if [ -d "dist" ]; then
    cp -r dist/* "$BACKEND_STATIC/"
    print_success "Frontend files copied to backend (from dist/)"
elif [ -d "build" ]; then
    cp -r build/* "$BACKEND_STATIC/"
    print_success "Frontend files copied to backend (from build/)"
else
    print_error "No dist/ or build/ directory found after frontend build"
    exit 1
fi

echo ""
echo "=============================================="
print_success "Frontend build and copy completed successfully!"
echo -e "${GREEN}Files copied to: $BACKEND_STATIC${NC}"
echo -e "${BLUE}Next steps:${NC}"
echo "  1. Restart backend: cd scripts/application && ./restart.sh"
echo "  2. Or full deploy: cd scripts/deployment && ./quick-deploy.sh"
echo "=============================================="