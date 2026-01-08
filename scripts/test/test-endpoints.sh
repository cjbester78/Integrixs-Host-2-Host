#!/bin/bash

# Script to test both HTTP and HTTPS endpoints

echo "=== H2H Dual Protocol Test Script ==="
echo "Testing both HTTP and HTTPS endpoints..."
echo ""

HTTP_URL="http://localhost:8080"
HTTPS_URL="https://localhost:8443"

echo "🔍 Testing HTTP endpoint: $HTTP_URL"
HTTP_RESPONSE=$(curl -s -w "\n%{http_code}" -k "$HTTP_URL" 2>/dev/null | tail -n1)

if [ "$HTTP_RESPONSE" = "200" ] || [ "$HTTP_RESPONSE" = "302" ] || [ "$HTTP_RESPONSE" = "401" ]; then
    echo "✅ HTTP endpoint is responding (Status: $HTTP_RESPONSE)"
else
    echo "❌ HTTP endpoint failed (Status: $HTTP_RESPONSE)"
fi

echo ""
echo "🔍 Testing HTTPS endpoint: $HTTPS_URL"
HTTPS_RESPONSE=$(curl -s -w "\n%{http_code}" -k "$HTTPS_URL" 2>/dev/null | tail -n1)

if [ "$HTTPS_RESPONSE" = "200" ] || [ "$HTTPS_RESPONSE" = "302" ] || [ "$HTTPS_RESPONSE" = "401" ]; then
    echo "✅ HTTPS endpoint is responding (Status: $HTTPS_RESPONSE)"
else
    echo "❌ HTTPS endpoint failed (Status: $HTTPS_RESPONSE)"
fi

echo ""
echo "🔍 Testing Health endpoints:"

echo "HTTP Health: $HTTP_URL/actuator/health"
HTTP_HEALTH=$(curl -s -w "\n%{http_code}" -k "$HTTP_URL/actuator/health" 2>/dev/null | tail -n1)
if [ "$HTTP_HEALTH" = "200" ]; then
    echo "✅ HTTP Health check OK"
else
    echo "❌ HTTP Health check failed (Status: $HTTP_HEALTH)"
fi

echo "HTTPS Health: $HTTPS_URL/actuator/health"
HTTPS_HEALTH=$(curl -s -w "\n%{http_code}" -k "$HTTPS_URL/actuator/health" 2>/dev/null | tail -n1)
if [ "$HTTPS_HEALTH" = "200" ]; then
    echo "✅ HTTPS Health check OK"
else
    echo "❌ HTTPS Health check failed (Status: $HTTPS_HEALTH)"
fi

echo ""
echo "=== Protocol Support Summary ==="
if [[ ("$HTTP_RESPONSE" = "200" || "$HTTP_RESPONSE" = "302" || "$HTTP_RESPONSE" = "401") && 
      ("$HTTPS_RESPONSE" = "200" || "$HTTPS_RESPONSE" = "302" || "$HTTPS_RESPONSE" = "401") ]]; then
    echo "🎉 SUCCESS: Both HTTP and HTTPS protocols are working!"
    echo "   📡 HTTP available at:  $HTTP_URL"
    echo "   🔒 HTTPS available at: $HTTPS_URL"
else
    echo "⚠️  Warning: Some endpoints may not be responding correctly"
fi
echo ""