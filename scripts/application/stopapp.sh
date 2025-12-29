#!/bin/bash

# H2H File Transfer - Stop Application Script

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
PID_FILE="$PROJECT_ROOT/backend/logs/app.pid"
PORT=8080

echo -e "${BLUE}$APP_NAME - Stopping Application${NC}"
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

# Function to stop process by PID
stop_by_pid() {
    local pid=$1
    print_status "Stopping application with PID: $pid"
    
    # Send SIGTERM
    if kill $pid 2>/dev/null; then
        print_status "Sent SIGTERM to process $pid"
        
        # Wait for graceful shutdown
        COUNTER=0
        MAX_WAIT=30
        
        while [ $COUNTER -lt $MAX_WAIT ]; do
            if ! ps -p $pid > /dev/null 2>&1; then
                print_success "Application stopped gracefully"
                return 0
            fi
            sleep 1
            COUNTER=$((COUNTER + 1))
        done
        
        # Force kill if still running
        print_warning "Application did not stop gracefully, forcing shutdown..."
        if kill -9 $pid 2>/dev/null; then
            print_success "Application forcefully stopped"
            return 0
        else
            print_error "Could not stop application"
            return 1
        fi
    else
        print_warning "Could not send SIGTERM to process $pid (may already be stopped)"
        return 1
    fi
}

# Function to stop by port
stop_by_port() {
    local port=$1
    print_status "Looking for processes using port $port"
    
    local pids=$(lsof -ti :$port 2>/dev/null || true)
    
    if [ -z "$pids" ]; then
        print_status "No processes found using port $port"
        return 1
    fi
    
    for pid in $pids; do
        print_status "Found process $pid using port $port"
        stop_by_pid $pid
    done
    
    return 0
}

# Check PID file first
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    print_status "Found PID file: $PID_FILE"
    
    if ps -p $PID > /dev/null 2>&1; then
        if stop_by_pid $PID; then
            rm -f "$PID_FILE"
            print_success "Application stopped and PID file removed"
            exit 0
        fi
    else
        print_warning "Process $PID from PID file is not running"
        rm -f "$PID_FILE"
        print_status "Removed stale PID file"
    fi
else
    print_status "No PID file found: $PID_FILE"
fi

# Try to stop by port
if stop_by_port $PORT; then
    print_success "Stopped processes using port $PORT"
    rm -f "$PID_FILE"
    exit 0
fi

# Look for H2H application processes
print_status "Looking for H2H File Transfer processes..."
H2H_PIDS=$(ps aux | grep "backend.*jar" | grep -v grep | awk '{print $2}' || true)

if [ -n "$H2H_PIDS" ]; then
    print_status "Found H2H application processes: $H2H_PIDS"
    for pid in $H2H_PIDS; do
        stop_by_pid $pid
    done
    rm -f "$PID_FILE"
    print_success "All H2H application processes stopped"
    exit 0
fi

# Look for any Java processes that might be our application
print_status "Looking for Java processes on port $PORT..."
JAVA_PIDS=$(ps aux | grep "java.*$PORT" | grep -v grep | awk '{print $2}' || true)

if [ -n "$JAVA_PIDS" ]; then
    print_status "Found Java processes that might be our application: $JAVA_PIDS"
    for pid in $JAVA_PIDS; do
        print_status "Checking process $pid..."
        if ps -p $pid -o args | grep -q "backend\|8080"; then
            stop_by_pid $pid
        fi
    done
    rm -f "$PID_FILE"
    exit 0
fi

print_warning "No running $APP_NAME application found"
print_status "Application may already be stopped"

# Clean up PID file if it exists
if [ -f "$PID_FILE" ]; then
    rm -f "$PID_FILE"
    print_status "Cleaned up PID file"
fi

echo ""
echo "=============================================="
print_success "Stop operation completed"
echo "=============================================="

exit 0