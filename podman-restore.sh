# podman-restore.sh - 데이터 복구 스크립트

#!/bin/bash
BACKUP_DIR=${1:-}

if [ -z "$BACKUP_DIR" ] || [ ! -d "$BACKUP_DIR" ]; then
    echo "❌ 사용법: $0 <백업_디렉토리>"
    echo "예: $0 ./backups/20250112_103000"
    exit 1
fi

echo "⚠️  경고: 현재 데이터가 복구 데이터로 대체됩니다!"
read -p "계속하시겠습니까? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "취소되었습니다."
    exit 0
fi

echo "♻️  데이터 복구 시작..."

# PostgreSQL 복구
if [ -f "$BACKUP_DIR/postgres_backup.sql" ]; then
    echo "📦 PostgreSQL 복구 중..."
    podman exec -i icao-local-pkd-postgres psql -U postgres icao_local_pkd < $BACKUP_DIR/postgres_backup.sql
fi


# 업로드 파일 복구
if [ -f "$BACKUP_DIR/uploads.tar.gz" ]; then
    echo "📦 업로드 파일 복구 중..."
    tar -xzf $BACKUP_DIR/uploads.tar.gz -C ./data/
fi

echo "✅ 복구 완료!"