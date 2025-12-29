# H2H File Transfer - Updated Scripts

The scripts have been updated to work with the new folder structure where the frontend is a separate React project.

## Folder Structure

```
Integrixs-Host-to-Host/
├── H2H-Backend/          # Java Maven project
│   ├── h2h-backend/
│   ├── h2h-core/
│   ├── h2h-shared/
│   ├── h2h-adapters/
│   └── scripts/          # Backend-specific scripts
└── H2H-Frontend/         # React TypeScript project
    ├── src/
    ├── dist/
    └── package.json
```

## Available Scripts

### Frontend Only
```bash
# Build just the frontend and copy to backend
cd scripts
./build-frontend.sh
```

### Backend Deployment
```bash
# Quick deploy (build frontend + restart backend)
cd H2H-Backend/scripts/deployment
./quick-deploy.sh

# Full deploy (with tests and migrations)
cd H2H-Backend/scripts/deployment
./deploy.sh
```

### Application Management
```bash
# Start/stop/restart backend
cd H2H-Backend/scripts/application
./startapp.sh
./stopapp.sh
./restart.sh
./status.sh
```

### Database
```bash
# Run migrations
cd H2H-Backend/scripts/database
./migrate.sh
```

## What Changed

1. **Frontend Path**: Updated from `h2h-frontend/` to `../H2H-Frontend/`
2. **Separate Build**: Frontend can now be built independently
3. **Clean Structure**: Backend and frontend are completely separate projects

## Development Workflow

1. **Frontend Development**:
   ```bash
   cd H2H-Frontend
   npm run dev
   ```

2. **Backend Development**:
   ```bash
   cd H2H-Backend/scripts/application
   ./startapp.sh
   ```

3. **Full Deployment**:
   ```bash
   cd H2H-Backend/scripts/deployment
   ./quick-deploy.sh
   ```