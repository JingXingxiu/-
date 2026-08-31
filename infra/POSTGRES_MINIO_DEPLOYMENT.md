# PostgreSQL 16 + MinIO 部署运行手册

本手册只对应 `docker-compose.postgres-minio.yml`。该编排由 PostgreSQL 16、MinIO、Spring Boot 后端和 H5/Nginx 组成，只有 H5 在宿主机回环地址 `127.0.0.1:18081` 监听；数据库、对象存储和后端均不发布宿主机端口。

旧的 `docker-compose.minio.yml` 只用于本机开发，会发布 MinIO 端口并使用另一套卷；服务器上不要启动它，也不要和本编排组合使用。

## 1. 首次准备

以下命令均在服务器的仓库目录执行。真实凭据只写入被 Git 忽略的 `infra/.env`，不要粘贴到 Compose、提交记录、日志或聊天中。

```bash
cd /srv/Books/infra
cp -n .env.example .env
chmod 600 .env
nano .env
```

必须填写：

- `POSTGRES_PASSWORD`
- `MINIO_ROOT_USER`
- `MINIO_ROOT_PASSWORD`
- 当 `ADMIN_API_ENABLED=true` 时的 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD`；管理密码必须不超过 72 个 UTF-8 字节（BCrypt 限制）

首次启动前先校验并拉取基础镜像；校验命令不会展开打印配置：

```bash
docker compose --env-file .env -f docker-compose.postgres-minio.yml config --quiet
docker compose --env-file .env -f docker-compose.postgres-minio.yml pull postgres minio
docker compose --env-file .env -f docker-compose.postgres-minio.yml build --pull backend web
```

## 2. 从当前 classpath 版本安全切换

先构建完整模式，确认构建成功后再停止旧站点。`stop` 会保留旧容器，便于立即回滚。

```bash
cd /srv/Books/infra
docker compose --env-file .env -f docker-compose.postgres-minio.yml config --quiet
docker compose --env-file .env -f docker-compose.postgres-minio.yml pull postgres minio
docker compose --env-file .env -f docker-compose.postgres-minio.yml build --pull backend web
docker compose -f docker-compose.classpath.yml stop web backend
docker compose --env-file .env -f docker-compose.postgres-minio.yml up -d --wait --wait-timeout 240
curl --fail --silent http://127.0.0.1:18081/backend/actuator/health
```

不要把 `docker compose down -v` 当作更新、停止或排障命令；`-v` 会删除 PostgreSQL 和 MinIO 的持久化卷。

## 3. 普通更新

`infra/.env` 被 Git 忽略，`git pull` 不会覆盖它。更新过程直接滚动重建服务，不需要先执行 `down`。

```bash
cd /srv/Books
git pull --ff-only origin main
cd infra
docker compose --env-file .env -f docker-compose.postgres-minio.yml config --quiet
docker compose --env-file .env -f docker-compose.postgres-minio.yml pull postgres minio
docker compose --env-file .env -f docker-compose.postgres-minio.yml build --pull backend web
docker compose --env-file .env -f docker-compose.postgres-minio.yml up -d --wait --wait-timeout 240 --remove-orphans
```

## 4. 状态与日志

```bash
cd /srv/Books/infra
docker compose --env-file .env -f docker-compose.postgres-minio.yml ps
docker compose --env-file .env -f docker-compose.postgres-minio.yml logs --tail 200 backend
docker compose --env-file .env -f docker-compose.postgres-minio.yml logs --tail 200 web
docker compose --env-file .env -f docker-compose.postgres-minio.yml logs --tail 200 postgres minio
docker stats --no-stream
```

日志使用单文件 10 MiB、最多三份的轮转策略，避免 40 GiB 系统盘被无界占满。不要在排障命令中执行 `docker inspect` 后公开粘贴完整 `Env`，它包含私有凭据。

## 5. 备份

在 `infra` 目录创建本地备份目录。PostgreSQL 使用一致性逻辑备份；MinIO 停止写入后备份 named volume。为了让数据库元数据和原始 CSV 尽量保持一致，备份窗口先停止后端。

```bash
cd /srv/Books/infra
mkdir -p backups
STAMP=$(date +%Y%m%d-%H%M%S)
docker compose --env-file .env -f docker-compose.postgres-minio.yml stop backend
docker compose --env-file .env -f docker-compose.postgres-minio.yml exec -T postgres sh -c 'pg_dump --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --format=custom' > "backups/postgres-$STAMP.dump"
docker compose --env-file .env -f docker-compose.postgres-minio.yml stop minio
docker run --rm -v books-minio-data:/source:ro -v "$PWD/backups:/backup" alpine:3.22 sh -c "tar -czf /backup/minio-$STAMP.tar.gz -C /source ."
docker compose --env-file .env -f docker-compose.postgres-minio.yml start minio
docker compose --env-file .env -f docker-compose.postgres-minio.yml up -d --wait --wait-timeout 240 backend web
```

将 `backups/` 定期复制到服务器外部；同一块 40 GiB 磁盘上的备份不能应对整盘故障。

## 6. 恢复

恢复会覆盖当前数据，只能在确认备份文件、已经额外备份当前状态并进入维护窗口后执行。下面的 `BACKUP_STAMP` 必须替换为同一批备份的时间戳。

```bash
cd /srv/Books/infra
BACKUP_STAMP=20260831-120000
docker compose --env-file .env -f docker-compose.postgres-minio.yml stop web backend minio
docker compose --env-file .env -f docker-compose.postgres-minio.yml exec -T postgres sh -c 'dropdb --if-exists --maintenance-db=postgres --username="$POSTGRES_USER" "$POSTGRES_DB" && createdb --maintenance-db=postgres --username="$POSTGRES_USER" "$POSTGRES_DB"'
docker compose --env-file .env -f docker-compose.postgres-minio.yml exec -T postgres sh -c 'pg_restore --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" --no-owner --clean --if-exists' < "backups/postgres-$BACKUP_STAMP.dump"
docker run --rm -v books-minio-data:/target -v "$PWD/backups:/backup:ro" alpine:3.22 sh -c "find /target -mindepth 1 -delete && tar -xzf /backup/minio-$BACKUP_STAMP.tar.gz -C /target"
docker compose --env-file .env -f docker-compose.postgres-minio.yml start minio
docker compose --env-file .env -f docker-compose.postgres-minio.yml up -d --wait --wait-timeout 240 backend web
curl --fail --silent http://127.0.0.1:18081/backend/actuator/health
```

## 7. 回滚到 classpath 版本

回滚只停止完整模式，不删除容器和卷；PostgreSQL 与 MinIO 数据会原样保留。

```bash
cd /srv/Books/infra
docker compose --env-file .env -f docker-compose.postgres-minio.yml stop web backend minio postgres
docker compose -f docker-compose.classpath.yml start backend web
curl --fail --silent http://127.0.0.1:18081/backend/actuator/health
```

需要再次切回完整模式时：

```bash
docker compose -f docker-compose.classpath.yml stop web backend
docker compose --env-file .env -f docker-compose.postgres-minio.yml up -d --wait --wait-timeout 240
```
