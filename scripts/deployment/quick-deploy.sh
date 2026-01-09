#!/bin/bash

# Integrixs Host 2 Host - Quick Deploy Script
# This script builds the frontend and restarts the backend for development

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

echo -e "${BLUE}Integrixs Host 2 Host - Quick Deploy${NC}"
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

print_status "Starting quick deployment..."
print_status "Project root: $PROJECT_ROOT"

# Stop existing application
print_status "Stopping existing application..."
if lsof -Pi :8080 -sTCP:LISTEN -t > /dev/null 2>&1; then
    PIDS=$(lsof -Pi :8080 -sTCP:LISTEN -t)
    for pid in $PIDS; do
        print_status "Stopping process $pid on port 8080"
        kill $pid 2>/dev/null || print_warning "Could not kill process $pid"
    done
    sleep 2
fi

# Clean backend static directory first
print_status "Cleaning backend static directory..."
BACKEND_STATIC="$PROJECT_ROOT/backend/src/main/resources/static"
mkdir -p "$BACKEND_STATIC"
rm -rf "$BACKEND_STATIC"/*
print_success "Backend static directory cleaned"

# Build frontend with Maven
print_status "Building frontend..."
cd "$PROJECT_ROOT"
mvn clean compile -pl frontend

if [ $? -eq 0 ]; then
    print_success "Frontend build completed"
else
    print_error "Frontend build failed"
    exit 1
fi

# Copy frontend files to backend static
print_status "Copying frontend files to backend..."
cp -r "$PROJECT_ROOT/frontend/dist/"* "$BACKEND_STATIC/"
print_success "Frontend files copied to backend"

# Build backend application (skip frontend since we already built it)
print_status "Building backend application..."
mvn compile -pl \!frontend

if [ $? -eq 0 ]; then
    print_success "Backend compilation completed"
else
    print_error "Backend compilation failed"
    exit 1
fi

# Package application JAR (skip frontend since we already built it)
print_status "Packaging application JAR..."
mvn package -DskipTests -pl \!frontend

if [ $? -eq 0 ]; then
    print_success "Application packaging completed"
else
    print_error "Application packaging failed"
    exit 1
fi

# Start application
print_status "Starting Integrixs Host 2 Host application..."
cd "$PROJECT_ROOT"

# Set environment variables
export JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

# Start in background
nohup java $JAVA_OPTS -jar backend/target/backend-1.0.0-SNAPSHOT.jar \
    --spring.profiles.active=$SPRING_PROFILES_ACTIVE \
    --server.port=8080 \
    > backend/logs/startup.log 2>&1 &

APP_PID=$!
print_status "Application started with PID: $APP_PID"

# Wait for application to start
print_status "Waiting for application to start..."
COUNTER=0
MAX_WAIT=60

while [ $COUNTER -lt $MAX_WAIT ]; do
    if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
        print_success "Application started successfully!"
        echo ""
        echo "=============================================="
        echo -e "${GREEN}Application is available at: http://localhost:8080${NC}"
        echo -e "${GREEN}Login with: Administrator / Int3grix@01${NC}"
        echo -e "${BLUE}PID: $APP_PID${NC}"
        echo "=============================================="
        exit 0
    fi
    
    # Check if process is still running
    if ! ps -p $APP_PID > /dev/null 2>&1; then
        print_error "Application process died during startup"
        print_error "Check the log file: backend/logs/startup.log"
        exit 1
    fi
    
    sleep 2
    COUNTER=$((COUNTER + 2))
    
    if [ $((COUNTER % 10)) -eq 0 ]; then
        print_status "Still waiting... ($COUNTER/$MAX_WAIT seconds)"
    fi
done

print_warning "Application may still be starting (timeout reached after $MAX_WAIT seconds)"
print_status "Check the log file: backend/logs/startup.log"
exit 0