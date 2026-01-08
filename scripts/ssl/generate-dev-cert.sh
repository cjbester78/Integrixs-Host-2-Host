#!/bin/bash

# Script to generate self-signed SSL certificate for H2H development

set -e

# Configuration
CERT_DIR="$(dirname "$0")/../../backend/src/main/resources/ssl"
KEYSTORE_NAME="h2h-dev-keystore.p12"
KEYSTORE_PASSWORD="h2h-dev-password"
CERT_ALIAS="h2h-dev"
KEY_SIZE=2048
VALIDITY_DAYS=365

# Certificate subject information
COUNTRY="ZA"
STATE="Western Cape"
CITY="Cape Town"
ORGANIZATION="Integrixs"
ORGANIZATIONAL_UNIT="Development"
COMMON_NAME="localhost"
EMAIL="dev@integrixs.com"

# Subject string
SUBJECT="CN=${COMMON_NAME}, OU=${ORGANIZATIONAL_UNIT}, O=${ORGANIZATION}, L=${CITY}, ST=${STATE}, C=${COUNTRY}, emailAddress=${EMAIL}"

echo "=== H2H Development SSL Certificate Generator ==="
echo "Creating SSL certificate for development environment..."

# Create SSL directory if it doesn't exist
mkdir -p "${CERT_DIR}"

# Generate keystore with self-signed certificate
echo "Generating PKCS12 keystore with self-signed certificate..."
keytool -genkeypair \
    -keyalg RSA \
    -keysize ${KEY_SIZE} \
    -validity ${VALIDITY_DAYS} \
    -alias "${CERT_ALIAS}" \
    -keystore "${CERT_DIR}/${KEYSTORE_NAME}" \
    -storetype PKCS12 \
    -storepass "${KEYSTORE_PASSWORD}" \
    -keypass "${KEYSTORE_PASSWORD}" \
    -dname "${SUBJECT}" \
    -ext "SAN=dns:localhost,dns:127.0.0.1,ip:127.0.0.1,ip:::1" \
    -v

echo ""
echo "=== Certificate Generated Successfully ==="
echo "Keystore location: ${CERT_DIR}/${KEYSTORE_NAME}"
echo "Keystore password: ${KEYSTORE_PASSWORD}"
echo "Certificate alias: ${CERT_ALIAS}"
echo "Valid for: ${VALIDITY_DAYS} days"
echo ""
echo "To use this certificate:"
echo "1. Update your application.yml with:"
echo "   server.ssl.key-store: classpath:ssl/${KEYSTORE_NAME}"
echo "   server.ssl.key-store-password: ${KEYSTORE_PASSWORD}"
echo "   server.ssl.key-alias: ${CERT_ALIAS}"
echo "2. Set h2h.server.ssl-enabled: true"
echo ""
echo "⚠️  This is a self-signed certificate for development only!"
echo "⚠️  Browsers will show security warnings - this is normal."
echo ""

# List certificate details
echo "Certificate details:"
keytool -list -v -alias "${CERT_ALIAS}" -keystore "${CERT_DIR}/${KEYSTORE_NAME}" -storepass "${KEYSTORE_PASSWORD}"

echo "=== Setup Complete ==="