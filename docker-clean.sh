#!/bin/bash
# docker-clean.sh - 완전 삭제 스크립트 (PostgreSQL + OpenLDAP + pgAdmin)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "⚠️  경고: 모든 데이터가 삭제됩니다!"
echo "   - PostgreSQL 데이터 (업로드 이력, PA 이력 등)"
echo "   - OpenLDAP 데이터 (인증서, CRL, Master List)"
echo "   - pgAdmin 설정"
echo ""
read -p "계속하시겠습니까? (yes/no): " confirm

if [ "$confirm" != "yes" ]; then
    echo "취소되었습니다."
    exit 0
fi

echo ""
echo "🗑️  컨테이너 중지 및 볼륨 삭제 중..."
docker compose down -v --remove-orphans

echo ""
echo "⏳ 컨테이너 완전 중지 대기 중..."
sleep 3

echo ""
echo "🗄️  기존 Docker 볼륨 삭제 중..."
# 이전 버전에서 생성된 Docker 볼륨 삭제
docker volume rm icao-local-pkd_openldap1_config 2>/dev/null || true
docker volume rm icao-local-pkd_openldap1_data 2>/dev/null || true
docker volume rm icao-local-pkd_openldap2_config 2>/dev/null || true
docker volume rm icao-local-pkd_openldap2_data 2>/dev/null || true
docker volume rm local-pkd_icao-local-pkd-openldap1_config 2>/dev/null || true
docker volume rm local-pkd_icao-local-pkd-openldap1_data 2>/dev/null || true
docker volume rm local-pkd_icao-local-pkd-postgres_data 2>/dev/null || true
echo "   ✓ Docker 볼륨 정리 완료"

echo ""
echo "📦 바인드 마운트 데이터 삭제 중..."

# PostgreSQL 데이터 삭제 (숨겨진 파일 포함)
if [ -d "./.docker-data/postgres" ]; then
    echo "   - PostgreSQL 데이터 삭제..."
    docker run --rm -v "$SCRIPT_DIR/.docker-data/postgres:/data" alpine sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null; ls -la /data/ 2>/dev/null || true"
fi

# pgAdmin 데이터 삭제 (숨겨진 파일 포함)
if [ -d "./.docker-data/pgadmin" ]; then
    echo "   - pgAdmin 데이터 삭제..."
    docker run --rm -v "$SCRIPT_DIR/.docker-data/pgadmin:/data" alpine sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null; ls -la /data/ 2>/dev/null || true"
fi

# OpenLDAP1 데이터 삭제 (숨겨진 파일 포함)
if [ -d "./.docker-data/openldap1" ]; then
    echo "   - OpenLDAP1 데이터 삭제..."
    docker run --rm -v "$SCRIPT_DIR/.docker-data/openldap1/data:/data" alpine sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null"
    docker run --rm -v "$SCRIPT_DIR/.docker-data/openldap1/config:/config" alpine sh -c "rm -rf /config/* /config/.[!.]* /config/..?* 2>/dev/null"
fi

# OpenLDAP2 데이터 삭제 (숨겨진 파일 포함)
if [ -d "./.docker-data/openldap2" ]; then
    echo "   - OpenLDAP2 데이터 삭제..."
    docker run --rm -v "$SCRIPT_DIR/.docker-data/openldap2/data:/data" alpine sh -c "rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null"
    docker run --rm -v "$SCRIPT_DIR/.docker-data/openldap2/config:/config" alpine sh -c "rm -rf /config/* /config/.[!.]* /config/..?* 2>/dev/null"
fi

# 네트워크 삭제 (선택적)
echo ""
echo "🌐 네트워크 정리 중..."
docker network rm local-pkd_default 2>/dev/null || true

echo ""
echo "✅ 삭제 완료!"
echo ""
echo "📌 다음 단계:"
echo "   1. ./docker-start.sh --skip-app  # 컨테이너 시작"
echo "   2. ./docker-ldap-init.sh         # LDAP 스키마 및 DIT 초기화"
echo "   3. 애플리케이션 시작              # ./mvnw spring-boot:run"
