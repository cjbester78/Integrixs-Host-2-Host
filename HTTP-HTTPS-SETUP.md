# HTTP and HTTPS Dual Protocol Support

The Integrixs Host-to-Host application now supports both HTTP and HTTPS protocols simultaneously.

## Configuration Overview

### Development Environment (dev profile)
- **HTTP**: Available on port `8080`
- **HTTPS**: Available on port `8443`
- **SSL Certificate**: Self-signed development certificate

### Production Environment (prod profile)
- **HTTP**: Available on port `8080` 
- **HTTPS**: Available on port `443`
- **SSL Certificate**: Production certificate required

## Quick Start

### 1. Generate Development SSL Certificate
```bash
# Generate self-signed certificate for development
./scripts/ssl/generate-dev-cert.sh
```

### 2. Start Application
```bash
# Start with development profile (enables dual protocols)
cd backend
mvn spring-boot:run -Dspring.profiles.active=dev
```

### 3. Test Both Protocols
```bash
# Test both HTTP and HTTPS endpoints
./scripts/test/test-endpoints.sh
```

## Access URLs

### Development
- **HTTP**: http://localhost:8080
- **HTTPS**: https://localhost:8443 (⚠️ Self-signed certificate warning expected)
- **Health Check (HTTP)**: http://localhost:8080/actuator/health
- **Health Check (HTTPS)**: https://localhost:8443/actuator/health

### Production
- **HTTP**: http://your-domain:8080
- **HTTPS**: https://your-domain:443
- **Health Check**: https://your-domain/actuator/health

## SSL Certificate Setup

### Development (Automatic)
The development setup includes an automatic self-signed certificate generator:

```bash
./scripts/ssl/generate-dev-cert.sh
```

This creates:
- Certificate location: `backend/src/main/resources/ssl/h2h-dev-keystore.p12`
- Password: `h2h-dev-password`
- Alias: `h2h-dev`
- Valid for: 365 days

### Production (Manual)
For production, you need to provide a real SSL certificate:

1. Obtain SSL certificate from a Certificate Authority
2. Convert to PKCS12 format if needed:
   ```bash
   openssl pkcs12 -export -in cert.crt -inkey cert.key -out keystore.p12
   ```
3. Place certificate at: `/opt/h2h/ssl/keystore.p12`
4. Set environment variable: `SSL_KEYSTORE_PASSWORD=your_password`

## Configuration Details

### Application Properties

#### Development Profile (`dev`)
```yaml
h2h:
  server:
    http-port: 8080
    https-port: 8443
    ssl-enabled: true

server:
  ssl:
    enabled: true
    key-store: classpath:ssl/h2h-dev-keystore.p12
    key-store-password: h2h-dev-password
    key-store-type: PKCS12
    key-alias: h2h-dev
```

#### Production Profile (`prod`)
```yaml
h2h:
  server:
    http-port: 8080
    https-port: 443
    ssl-enabled: true

server:
  ssl:
    enabled: true
    key-store: /opt/h2h/ssl/keystore.p12
    key-store-password: ${SSL_KEYSTORE_PASSWORD:changeme}
    key-store-type: PKCS12
    key-alias: h2h
```

### Server Configuration Class
The `ServerConfig.java` class handles the dual connector setup:
- Primary connector: HTTPS (when SSL enabled)
- Additional connector: HTTP (always available)

## Frontend Configuration

### Development
Frontend (port 3000) proxies to HTTPS backend:
```typescript
proxy: {
  '/api': {
    target: 'https://localhost:8443',
    changeOrigin: true,
    secure: false, // Allow self-signed certificates
  },
  '/ws': {
    target: 'wss://localhost:8443',
    ws: true,
    secure: false,
  },
}
```

### Production
Frontend served directly from backend, both protocols available.

## Browser Considerations

### Self-Signed Certificate Warnings
Development HTTPS will show browser security warnings:
- **Chrome**: Click "Advanced" → "Proceed to localhost (unsafe)"
- **Firefox**: Click "Advanced" → "Accept the Risk and Continue"
- **Safari**: Click "Show Details" → "Visit this website"

This is normal and expected for self-signed certificates.

### Production Certificates
Use certificates from trusted CAs to avoid browser warnings in production.

## Troubleshooting

### Common Issues

1. **Port already in use**
   ```bash
   # Check which process is using port 8080 or 8443
   lsof -i :8080
   lsof -i :8443
   ```

2. **SSL Certificate not found**
   ```
   Error: Could not find key store classpath:ssl/h2h-dev-keystore.p12
   ```
   Solution: Run `./scripts/ssl/generate-dev-cert.sh`

3. **Browser refuses HTTPS connection**
   - Ensure certificate is properly generated
   - Check browser console for specific errors
   - Try clearing browser cache/cookies

4. **Frontend can't connect to backend**
   - Verify backend is running on expected ports
   - Check frontend proxy configuration in `vite.config.ts`
   - Ensure CORS settings if needed

### Log Analysis
Check application logs for SSL-related issues:
```bash
tail -f backend/logs/h2h-dev.log
```

Look for:
- SSL connector initialization messages
- Port binding confirmations
- Certificate loading status

## Security Notes

### Development
- Self-signed certificates provide encryption but not identity verification
- Suitable for local development only
- Never use in production

### Production
- Always use certificates from trusted Certificate Authorities
- Regularly renew certificates before expiration
- Consider automating certificate renewal (Let's Encrypt, etc.)
- Monitor certificate expiration dates

## Environment Variables

### Required for Production
```bash
export SSL_KEYSTORE_PASSWORD=your_secure_password
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_db_password
```

### Optional Configuration
```bash
export H2H_HTTP_PORT=8080        # HTTP port (default: 8080)
export H2H_HTTPS_PORT=443        # HTTPS port (default: 443 for prod, 8443 for dev)
export H2H_SSL_ENABLED=true      # Enable SSL (default: profile-dependent)
```

## Testing Checklist

- [ ] HTTP endpoint responds (port 8080)
- [ ] HTTPS endpoint responds (port 8443/443)
- [ ] Health checks work on both protocols
- [ ] Frontend can connect via HTTPS
- [ ] SSL certificate is valid and properly configured
- [ ] No SSL/TLS errors in logs
- [ ] Browser security warnings handled appropriately

Use `./scripts/test/test-endpoints.sh` for automated testing.