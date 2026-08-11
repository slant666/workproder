# Progress

## Current Task

T021: Docker 化和一键部署

## Status

Implementation complete. Docker Desktop is installed and the WSL2 Docker engine is running. Compose files and deployment docs have been added. The Docker stack has been built and started successfully for a local trial run. No required implementation steps remain.

## Completed Work

- Verified Docker was initially missing.
- Guided Docker Desktop installation and WSL2/virtualization setup without touching local MySQL data.
- Confirmed Docker engine status after setup:
  - `HyperVisorPresent=True`
  - `docker-desktop` WSL distro running on version 2
  - Docker server available: `29.6.2 linux Docker Desktop`
- Added Docker deployment files:
  - root `.dockerignore`
  - root `.env.example`
  - root `docker-compose.yml`
  - `backend/Dockerfile`
  - `frontend/Dockerfile`
  - `frontend/nginx.conf`
- Added deployment documentation in `deploy/README.md`:
  - requirements;
  - first-time `.env` setup;
  - start and stop commands;
  - health checks;
  - database backup and restore;
  - uploaded-file backup and restore;
  - host port notes.
- Created a local ignored `.env` from `.env.example` for trial startup.
- Started the Docker stack successfully:
  - `work-order-mysql` healthy on host port `3307`;
  - `work-order-backend` healthy inside the Compose network;
  - `work-order-frontend` healthy on host port `8088`.

## Design Decisions

- The containerized MySQL is isolated from local development MySQL.
- Compose maps MySQL to host port `3307` by default to avoid local `3306` conflicts.
- Backend is only reachable inside the Compose network; frontend Nginx exposes the public HTTP port and proxies `/api`.
- MySQL data persists in the `mysql-data` Docker volume.
- Uploaded files persist in the `backend-uploads` Docker volume.
- Passwords and bootstrap token are provided through `.env` variables and are not hard-coded.

## Changed Files

- `.dockerignore`
- `.env.example`
- `docker-compose.yml`
- `backend/Dockerfile`
- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `deploy/README.md`
- `PROGRESS.md`

## Verification

- `mvn test` from `backend`: BUILD SUCCESS, 108 tests run, 0 failures, 0 errors, 0 skipped.
- `npm.cmd run test` from `frontend`: 1 test file passed, 40 tests passed.
- `docker compose config` with temporary non-secret environment values: passed.
- `docker compose up -d --build`: succeeded after pre-pulling base images and isolating container DB environment variable names from local development variables.
- `docker compose ps`: MySQL, backend, and frontend are up and healthy.
- `Invoke-WebRequest http://localhost:8088/`: HTTP 200.
- `Invoke-WebRequest http://localhost:8088/actuator/health`: backend health response proxied through Nginx.

## Notes

- Do not commit real `.env` secrets.
- Do not expose backend directly unless needed for debugging.
- Do not map container MySQL to host `3306` while local MySQL80 is used.
