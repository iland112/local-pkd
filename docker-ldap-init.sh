#!/bin/bash
# docker-ldap-init.sh - OpenLDAP ICAO PKD DIT 구조 초기화 스크립트

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 OpenLDAP ICAO PKD DIT 구조 초기화..."

# 컨테이너가 실행 중인지 확인
if ! docker ps | grep -q icao-local-pkd-openldap1; then
    echo "❌ openldap1 컨테이너가 실행 중이지 않습니다."
    echo "   먼저 ./docker-start.sh --skip-app 을 실행하세요."
    exit 1
fi

echo ""
echo "⏳ OpenLDAP 시작 대기 중..."
sleep 5

# PKD DIT 구조 생성
echo ""
echo "📁 ICAO PKD DIT 구조 생성 중..."

# dc=pkd 컨테이너 생성
docker exec icao-local-pkd-openldap1 ldapadd -x \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w core \
    -H ldap://localhost <<EOF || true
dn: dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: dcObject
objectClass: organization
dc: pkd
o: ICAO PKD
EOF

# dc=download,dc=pkd 컨테이너 생성
docker exec icao-local-pkd-openldap1 ldapadd -x \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w core \
    -H ldap://localhost <<EOF || true
dn: dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: dcObject
objectClass: organization
dc: download
o: PKD Download
EOF

# dc=data,dc=download,dc=pkd 컨테이너 생성
docker exec icao-local-pkd-openldap1 ldapadd -x \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w core \
    -H ldap://localhost <<EOF || true
dn: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: dcObject
objectClass: organization
dc: data
o: PKD Data
EOF

# dc=nc-data,dc=download,dc=pkd 컨테이너 생성
docker exec icao-local-pkd-openldap1 ldapadd -x \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w core \
    -H ldap://localhost <<EOF || true
dn: dc=nc-data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: dcObject
objectClass: organization
dc: nc-data
o: PKD Non-Compliant Data
EOF

echo ""
echo "✅ ICAO PKD DIT 구조 초기화 완료!"
echo ""
echo "📊 현재 DIT 구조:"
docker exec icao-local-pkd-openldap1 ldapsearch -x \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w core \
    -H ldap://localhost \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -s sub "(objectClass=*)" dn | grep "^dn:"

echo ""
echo "📌 접속 정보:"
echo "   - OpenLDAP 1:     ldap://localhost:389"
echo "   - OpenLDAP 2:     ldap://localhost:390"
echo "   - phpLDAPadmin:   http://localhost:8080"
echo "   - Admin DN:       cn=admin,dc=ldap,dc=smartcoreinc,dc=com"
echo "   - Admin Password: core"
