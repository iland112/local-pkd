#!/bin/bash
# docker-backup.sh - 데이터 백업 스크립트

BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"

echo "💾 데이터 백업 시작..."
mkdir -p $BACKUP_DIR

# PostgreSQL 백업
echo "📦 PostgreSQL 백업 중..."
docker exec icao-local-pkd-postgres pg_dump -U postgres icao_local_pkd > $BACKUP_DIR/postgres_backup.sql

# 업로드 파일 백업
if [ -d "./data/uploads" ]; then
    echo "📦 업로드 파일 백업 중..."
    tar -czf $BACKUP_DIR/uploads.tar.gz ./data/uploads
else
    echo "⚠️  업로드 파일 디렉토리가 없습니다. 건너뜁니다."
fi

echo ""
echo "✅ 백업 완료: $BACKUP_DIR"
ls -lh $BACKUP_DIR
