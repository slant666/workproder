# Docker deployment

This deployment is separate from the local development MySQL instance. The Compose MySQL service stores data in the `mysql-data` Docker volume and maps to host port `3307` by default, so it does not use or overwrite local MySQL data on `3306`.

## Requirements

- Docker Desktop for Windows with WSL 2 backend enabled.
- Docker CLI and Compose plugin available.
- Verify:

```powershell
docker --version
docker compose version
docker info
```

## First-time setup

From the project root:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and replace every `change-me-*` value with strong private values:

- `WORK_ORDER_DB_PASSWORD`
- `WORK_ORDER_CONTAINER_DB_PASSWORD`
- `WORK_ORDER_CONTAINER_DB_ROOT_PASSWORD`
- `WORK_ORDER_BOOTSTRAP_ADMIN_TOKEN`

Keep `.env` out of Git.

## Start

```powershell
docker compose up -d --build
```

Open:

```text
http://localhost:8088
```

Check status:

```powershell
docker compose ps
docker compose logs -f backend
```

## Stop

Stop containers but keep database and upload volumes:

```powershell
docker compose down
```

Stop and remove containers plus volumes. This deletes the containerized MySQL data and uploaded files, but still does not touch local host MySQL:

```powershell
docker compose down -v
```

## Health checks

- MySQL: `mysqladmin ping` inside the MySQL container.
- Backend: `http://backend:8080/actuator/health` inside the Compose network.
- Frontend: `http://127.0.0.1/` inside the Nginx container.

## Back up database

Create a backup directory:

```powershell
New-Item -ItemType Directory -Force deploy\backups
```

Dump the containerized database:

```powershell
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > deploy\backups\work_order_system.sql
```

For a timestamped backup:

```powershell
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
docker compose exec -T mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' > "deploy\backups\work_order_system-$stamp.sql"
```

## Restore database

Restore into the containerized MySQL database:

```powershell
Get-Content deploy\backups\work_order_system.sql -Raw | docker compose exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'
```

If restoring into a fresh environment, start MySQL first and wait until it is healthy:

```powershell
docker compose up -d mysql
docker compose ps
```

## Uploaded files

Uploaded work-order files are stored in the `backend-uploads` Docker volume at `/app/uploads` inside the backend container.

Back up uploaded files:

```powershell
docker run --rm -v work-order-system_backend-uploads:/data -v ${PWD}\deploy\backups:/backup alpine tar czf /backup/backend-uploads.tgz -C /data .
```

Restore uploaded files:

```powershell
docker run --rm -v work-order-system_backend-uploads:/data -v ${PWD}\deploy\backups:/backup alpine sh -c "rm -rf /data/* && tar xzf /backup/backend-uploads.tgz -C /data"
```

## Ports

- Frontend: host `8088` to container `80`.
- MySQL: host `3307` to container `3306`.
- Backend is not exposed to the host by default; Nginx proxies `/api` to it through the Compose network.
- The Compose database username/password variables use the `WORK_ORDER_CONTAINER_*` prefix so they do not conflict with local development environment variables such as `WORK_ORDER_DB_USERNAME=root`.

Change host ports in `.env` if needed:

```text
WORK_ORDER_HTTP_PORT=8088
WORK_ORDER_MYSQL_HOST_PORT=3307
```
