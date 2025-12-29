#!/bin/bash

# H2H File Transfer - Start Application Script

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

# Application settings
APP_NAME="H2H File Transfer"
JAR_FILE="$PROJECT_ROOT/backend/target/backend-1.0.0-SNAPSHOT.jar"
PID_FILE="$PROJECT_ROOT/backend/logs/app.pid"
LOG_FILE="$PROJECT_ROOT/backend/logs/startup.log"
PORT=8080

echo -e "${BLUE}$APP_NAME - Starting Application${NC}"
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

# Check if application is already running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1; then
        print_warning "Application is already running with PID $PID"
        echo -e "${YELLOW}Use stopapp.sh to stop it first${NC}"
        exit 1
    else
        print_status "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Check if port is already in use
if lsof -Pi :$PORT -sTCP:LISTEN -t > /dev/null 2>&1; then
    print_error "Port $PORT is already in use"
    echo -e "${RED}Please stop the process using port $PORT or choose a different port${NC}"
    exit 1
fi

# Check if JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    print_error "JAR file not found: $JAR_FILE"
    echo -e "${RED}Please build the application first using: mvn clean install${NC}"
    exit 1
fi

# Create logs directory
mkdir -p "$PROJECT_ROOT/backend/logs"

# Set environment variables
export JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"

print_status "Starting $APP_NAME..."
print_status "JAR file: $JAR_FILE"
print_status "Profile: $SPRING_PROFILES_ACTIVE"
print_status "Port: $PORT"
print_status "Java options: $JAVA_OPTS"

# Start application in background
cd "$PROJECT_ROOT"
nohup java $JAVA_OPTS -jar "$JAR_FILE" \
    --spring.profiles.active=$SPRING_PROFILES_ACTIVE \
    --server.port=$PORT \
    > "$LOG_FILE" 2>&1 &

# Store PID
APP_PID=$!
echo $APP_PID > "$PID_FILE"

print_status "Application started with PID: $APP_PID"
print_status "Log file: $LOG_FILE"

# Wait for application to start
print_status "Waiting for application to start..."
COUNTER=0
MAX_WAIT=60

while [ $COUNTER -lt $MAX_WAIT ]; do
    if curl -f http://localhost:$PORT/actuator/health > /dev/null 2>&1; then
        print_success "$APP_NAME started successfully!"
        echo ""
        echo "=============================================="
        echo -e "${GREEN}Application is running at: http://localhost:$PORT${NC}"
        echo -e "${GREEN}Login with: Administrator / Int3grix@01${NC}"
        echo -e "${BLUE}PID: $APP_PID${NC}"
        echo -e "${BLUE}Log file: $LOG_FILE${NC}"
        echo "=============================================="
        exit 0
    fi
    
    # Check if process is still running
    if ! ps -p $APP_PID > /dev/null 2>&1; then
        print_error "Application process died during startup"
        print_error "Check the log file for details: $LOG_FILE"
        rm -f "$PID_FILE"
        exit 1
    fi
    
    sleep 2
    COUNTER=$((COUNTER + 2))
    
    if [ $((COUNTER % 10)) -eq 0 ]; then
        print_status "Still waiting... ($COUNTER/$MAX_WAIT seconds)"
    fi
done

print_warning "Application may still be starting (timeout reached after $MAX_WAIT seconds)"
print_status "Check the log file for details: $LOG_FILE"
print_status "Application PID: $APP_PID"

exit 0