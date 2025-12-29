#!/bin/bash

# H2H File Transfer - Application Status Script

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
PID_FILE="$PROJECT_ROOT/backend/logs/app.pid"
LOG_FILE="$PROJECT_ROOT/backend/logs/startup.log"
PORT=8080

echo -e "${BLUE}$APP_NAME - Application Status${NC}"
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

# Check PID file
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    echo -e "${BLUE}PID File:${NC} $PID_FILE (PID: $PID)"
    
    if ps -p $PID > /dev/null 2>&1; then
        print_success "Application is running (PID: $PID)"
        
        # Get process details
        PROCESS_INFO=$(ps -p $PID -o pid,ppid,user,%cpu,%mem,vsz,rss,etime,command --no-headers)
        echo -e "${BLUE}Process Details:${NC}"
        echo "$PROCESS_INFO"
        
    else
        print_warning "PID file exists but process is not running"
        print_status "Stale PID file: $PID_FILE"
    fi
else
    print_status "No PID file found: $PID_FILE"
fi

echo ""

# Check port
echo -e "${BLUE}Port Status:${NC}"
if lsof -Pi :$PORT -sTCP:LISTEN -t > /dev/null 2>&1; then
    PORT_PIDS=$(lsof -Pi :$PORT -sTCP:LISTEN -t)
    print_success "Port $PORT is in use by PID(s): $PORT_PIDS"
    
    for pid in $PORT_PIDS; do
        PROCESS_CMD=$(ps -p $pid -o command --no-headers)
        echo -e "  ${BLUE}PID $pid:${NC} $PROCESS_CMD"
    done
else
    print_warning "Port $PORT is not in use"
fi

echo ""

# Check application health
echo -e "${BLUE}Health Check:${NC}"
if curl -f http://localhost:$PORT/actuator/health > /dev/null 2>&1; then
    print_success "Application health check passed"
    
    # Get health details
    HEALTH_JSON=$(curl -s http://localhost:$PORT/actuator/health 2>/dev/null || echo '{}')
    echo -e "${BLUE}Health Details:${NC} $HEALTH_JSON"
    
    # Try to get application info
    echo ""
    echo -e "${BLUE}Application Info:${NC}"
    if curl -f http://localhost:$PORT/actuator/info > /dev/null 2>&1; then
        INFO_JSON=$(curl -s http://localhost:$PORT/actuator/info 2>/dev/null || echo '{}')
        echo "$INFO_JSON"
    else
        print_status "Info endpoint not available"
    fi
    
else
    print_error "Application health check failed"
    print_status "Application may not be running or still starting up"
fi

echo ""

# Check for H2H processes
echo -e "${BLUE}H2H Processes:${NC}"
H2H_PROCESSES=$(ps aux | grep -E "(backend.*jar|H2HBackendApplication)" | grep -v grep | awk '{print $2, $11, $12, $13, $14, $15}' || true)

if [ -n "$H2H_PROCESSES" ]; then
    echo "$H2H_PROCESSES"
else
    print_status "No H2H File Transfer processes found"
fi

echo ""

# Check log file
echo -e "${BLUE}Log Files:${NC}"
if [ -f "$LOG_FILE" ]; then
    LOG_SIZE=$(du -h "$LOG_FILE" | cut -f1)
    LOG_MODIFIED=$(stat -f "%Sm" "$LOG_FILE" 2>/dev/null || stat -c "%y" "$LOG_FILE" 2>/dev/null || echo "Unknown")
    print_success "Startup log: $LOG_FILE ($LOG_SIZE, modified: $LOG_MODIFIED)"
    
    # Show last few lines
    echo -e "${BLUE}Last 5 lines of startup log:${NC}"
    tail -n 5 "$LOG_FILE" 2>/dev/null || print_status "Could not read log file"
else
    print_status "Startup log not found: $LOG_FILE"
fi

# Check application logs
APP_LOG="$PROJECT_ROOT/backend/logs/h2h-dev.log"
if [ -f "$APP_LOG" ]; then
    APP_LOG_SIZE=$(du -h "$APP_LOG" | cut -f1)
    APP_LOG_MODIFIED=$(stat -f "%Sm" "$APP_LOG" 2>/dev/null || stat -c "%y" "$APP_LOG" 2>/dev/null || echo "Unknown")
    print_success "Application log: $APP_LOG ($APP_LOG_SIZE, modified: $APP_LOG_MODIFIED)"
else
    print_status "Application log not found: $APP_LOG"
fi

echo ""

# Summary
echo "=============================================="
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p $PID > /dev/null 2>&1 && curl -f http://localhost:$PORT/actuator/health > /dev/null 2>&1; then
        print_success "$APP_NAME is running and healthy"
        echo -e "${GREEN}URL: http://localhost:$PORT${NC}"
        echo -e "${GREEN}Login: Administrator / Int3grix@01${NC}"
    elif ps -p $PID > /dev/null 2>&1; then
        print_warning "$APP_NAME is running but may not be ready"
        echo -e "${YELLOW}Check logs or wait for startup to complete${NC}"
    else
        print_error "$APP_NAME is not running"
    fi
else
    print_error "$APP_NAME is not running"
fi
echo "=============================================="