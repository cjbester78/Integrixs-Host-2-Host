# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Integrixs Host-to-Host (H2H) is an enterprise file transfer and integration platform built for banking operations with:
- **Backend**: Java 17, Spring Boot 3.2.0, PostgreSQL
- **Frontend**: React 18, TypeScript, Vite, TanStack Query, shadcn/ui
- **Architecture**: Multi-module Maven project with microservices

## Build and Run Commands

### Backend (Java/Spring Boot)

```bash
# Build entire project
mvn clean install

# Run backend only
cd backend
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring.profiles.active=dev

# Run tests
mvn test

# Run specific module tests
mvn test -pl backend
mvn test -pl adapters
mvn test -pl core
```

### Frontend (React/Vite)

```bash
cd frontend

# Install dependencies
npm install

# Development server
npm run dev

# Build production
npm run build

# Run linting
npm run lint

# Fix linting issues
npm run lint:fix

# Type checking
npm run type-check
```

### Database

PostgreSQL database configuration:
- **Database**: `h2h_dev`
- **Username**: `h2h_user` (or from `DB_USERNAME` env var)
- **Password**: `h2h_dev_password` (or from `DB_PASSWORD` env var)
- **Port**: 5432

Database migrations are located in `backend/src/main/resources/db/migration/` but Flyway is disabled in Spring Boot configuration.

## Architecture Overview

### Module Structure

The project uses a multi-module Maven architecture:

- **shared**: Common models, DTOs, and utilities shared across modules
- **core**: Core business logic, services, and repositories
- **adapters**: File transfer adapter implementations (Email, File, SFTP)
- **backend**: Main Spring Boot application with REST APIs and web controllers

### Key Architectural Patterns

1. **Adapter Pattern**
   - AbstractAdapterExecutor base class for all adapters
   - Separate implementations for File, SFTP, and Email adapters
   - Configuration stored as JSON in database

2. **Multi-layered Architecture**
   - Controllers handle HTTP requests and responses
   - Services contain business logic
   - Repositories handle data access
   - Adapters manage external system integrations

3. **Security Architecture**
   - JWT token-based authentication
   - Custom user details service
   - Role-based access control
   - CORS configuration for frontend integration

### Database Schema Patterns

- Entity models in `shared/src/main/java/com/integrixs/shared/model/`
- Repositories in `core/src/main/java/com/integrixs/core/repository/`
- Flyway migrations in `backend/src/main/resources/db/migration/`
- PostgreSQL-specific features and JSONB columns

### Frontend Architecture

- Component-based React architecture with TypeScript
- TanStack Query for server state management
- Zustand for client state management
- shadcn/ui component library
- Vite for build tooling and development server

## Development Patterns

### Adding New Adapters

1. Create adapter implementation in `adapters/src/main/java/com/integrixs/adapters/`
2. Extend AbstractAdapterExecutor from core module
3. Add configuration classes for adapter-specific settings
4. Create corresponding frontend components for configuration
5. Register adapter in factory pattern

### API Endpoints

- All APIs under `/api/*` prefix
- JWT authentication required for protected endpoints
- Standard response format using ApiResponse wrapper
- Controllers in `backend/src/main/java/com/integrixs/backend/controller/`

### Database Migrations

- Create migrations in `backend/src/main/resources/db/migration/`
- Follow naming convention: `V{number}__{description}.sql`
- PostgreSQL-specific syntax supported
- Manual migration execution (Flyway disabled in Spring Boot)

### Testing Approach

- Unit tests alongside source files
- JUnit 5 and Mockito for testing
- Testcontainers for integration tests
- Use `@SpringBootTest` for full application context tests

## Important Configuration

### Spring Profiles

- **dev**: Local development (default) - uses PostgreSQL
- **prod**: Production environment
- **docker**: Docker deployment

### Environment Variables

Key environment variables:
- `DB_USERNAME`: Database username (default: h2h_user)
- `DB_PASSWORD`: Database password (default: h2h_dev_password)
- `H2H_BASE_PATH`: Base path for file operations
- `H2H_ENVIRONMENT`: Environment identifier
- `H2H_JWT_SECRET`: JWT signing secret for production

### Application Configuration

Core configuration in `backend/src/main/resources/application.yml`:
- Database connection settings
- JWT configuration with secrets
- File processing settings (temp/archive directories)
- SFTP connection pool configuration
- Adapter execution timeouts and retry settings
- CORS configuration for frontend
- Logging configuration with correlation IDs

## Default Credentials

For testing authentication:
- **Username**: `Administrator`
- **Password**: `Int3grix@01`

## Key Files to Understand

1. **Backend Architecture**
   - `backend/src/main/java/com/integrixs/H2HBackendApplication.java` - Entry point
   - `core/src/main/java/com/integrixs/core/adapter/AbstractAdapterExecutor.java` - Adapter base
   - `shared/src/main/java/com/integrixs/shared/model/` - Entity definitions

2. **Frontend Architecture**
   - `frontend/src/App.tsx` - Route definitions and app structure
   - `frontend/src/lib/api.ts` - API service layer
   - `frontend/src/hooks/auth.ts` - Authentication hooks
   - `frontend/src/components/` - React components

3. **Configuration**
   - `backend/src/main/resources/application.yml` - Spring configuration
   - `frontend/vite.config.ts` - Frontend build configuration
   - `pom.xml` files - Maven build configuration

## Common Development Tasks

### Debug Backend
```bash
# Enable debug logging
mvn spring-boot:run -Dlogging.level.com.integrixs=DEBUG

# Remote debugging
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

### Database Operations
```bash
# Connect to PostgreSQL (development)
psql -h localhost -p 5432 -U h2h_user -d h2h_dev
# Password: h2h_dev_password
```

### Full Stack Development
```bash
# Terminal 1 - Backend
cd backend && mvn spring-boot:run

# Terminal 2 - Frontend  
cd frontend && npm run dev
```

### Production Build
```bash
# Backend
mvn clean package -P prod

# Frontend
cd frontend && npm run build
```