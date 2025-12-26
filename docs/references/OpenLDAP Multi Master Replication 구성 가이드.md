## 0. 설치 환경 정보
- **OS**: Debian 12
- **서버**: 2대 (Master1, Master2)
- **프로토콜**: ldap:// (TLS 없음)
- **서버 IP**:
  - Master1: 192.168.100.10
  - Master2: 192.168.100.11
- **도메인**: ldap.smartcoreinc.com
- openldap 서버 구성을 위한 ldif 파일 저장 위치: `/root/ldifs/*.ldif` 
## 1. OpenLDAP 패키지 설치 (양쪽 서버)
``` bash
#  기존에 패키지가 설치되어 재 설치 해야한다면 (선택)
sudo systemctl stop slapd
sudo apt-get remove --purge slapd
sudo rm -rf /var/lib/ldap/
sudo rm -rf /etc/ldap/ /etc/openldap/
sudo apt-get autoremove  
sudo apt-get clean
 
# 패키지 업데이트 및 설치
sudo apt update
sudo apt install slapd ldap-utils -y

# 초기 설정 (관리자 패스워드 설정)
sudo dpkg-reconfigure slapd
```

## 2. 기본 디렉토리 구조 설정

### Master1에서 실행  
```bash
# 관리자 계정으로 접속 테스트
ldapwhoami -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -W -H ldap://192.168.100.10

# 기본 OU 구조 생성
cat > base_structure.ldif << 'EOF'
dn: ou=people,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: people

dn: ou=groups,dc=ldap,dc=smartcoreinc,dc=com
objectClass: organizationalUnit
ou: groups
EOF

ldapadd -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -W -f base_structure.ldif -H ldap://192.168.100.10
``` 
## 3. syncprov 모듈 로드 (양쪽 서버)
```bash

# syncprov 모듈 로드를 위한 LDIF 파일 생성

cat > load_syncprov.ldif << 'EOF'
dn: cn=module{0},cn=config
changetype: modify
add: olcModuleLoad
olcModuleLoad: syncprov
EOF

# 모듈 로드
sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f load_syncprov.ldif
```
## 4. ServerID 설정
먼저 현재 서버의 실제 IP를 확인하고 올바른 ServerID를 설정해야 합니다.
### 현재 IP 확인

```bash

# 실제 서버 IP 확인
ip addr show | grep inet
hostname -I
```
### Master1에서 실행 (실제 IP로 변경 필요)

```bash

# 기존 ServerID가 있다면 삭제
cat > remove_serverid.ldif << 'EOF'
dn: cn=config
changetype: modify
delete: olcServerID
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f remove_serverid.ldif 2>/dev/null || true

# 새로운 ServerID 설정 (Master1 - 실제 IP로 변경)
cat > serverid1.ldif << 'EOF'
dn: cn=config
changetype: modify
add: olcServerID
olcServerID: 1
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f serverid1.ldif
```

### Master2에서 실행 (실제 IP로 변경 필요)

```bash

# 기존 ServerID가 있다면 삭제
cat > remove_serverid.ldif << 'EOF'
dn: cn=config
changetype: modify
delete: olcServerID
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f remove_serverid.ldif 2>/dev/null || true
  
# 새로운 ServerID 설정 (Master2 - 실제 IP로 변경)
cat > serverid2.ldif << 'EOF'
dn: cn=config
changetype: modify
add: olcServerID
olcServerID: 2
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f serverid2.ldif
```
### slapd 서비스 설정 확인 및 수정

**/etc/default/slapd 파일 수정**
```bash

# 설정 파일 백업
sudo cp /etc/default/slapd /etc/default/slapd.bak

# 설정 파일 편집
sudo nano /etc/default/slapd

# 다음 줄을 찾아서 수정:
# SLAPD_SERVICES="ldap:/// ldapi:///"
SLAPD_SERVICES="ldap://0.0.0.0:389/ ldapi:///"

# 또는 특정 IP로 바인딩하려면 (Master1 예시):
# SLAPD_SERVICES="ldap://192.168.100.10:389/ ldapi:///"
```
## 5. Database에 syncprov overlay 추가 (양쪽 서버)

```bash
cat > syncprov_overlay.ldif << 'EOF'
dn: olcOverlay=syncprov,olcDatabase={1}mdb,cn=config
changetype: add
objectClass: olcOverlayConfig
objectClass: olcSyncProvConfig
olcOverlay: syncprov
olcSpSessionLog: 100
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f syncprov_overlay.ldif
```
## 6. Multi Master Replication 설정

### Master1에서 실행
```bash
cat > multimaster_repl1.ldif << 'EOF'
dn: olcDatabase={1}mdb,cn=config
changetype: modify
add: olcSyncRepl
olcSyncRepl: {0}rid=001 provider=ldap://192.168.100.10 binddn="cn=admin,dc=ldap,dc=smartcoreinc,dc=com" bindmethod=simple credentials=core searchbase="dc=ldap,dc=smartcoreinc,dc=com" scope=sub schemachecking=on type=refreshAndPersist retry="30 5 300 3" timeout=1

olcSyncRepl: {1}rid=002 provider=ldap://192.168.100.11 binddn="cn=admin,dc=ldap,dc=smartcoreinc,dc=com" bindmethod=simple credentials=core searchbase="dc=ldap,dc=smartcoreinc,dc=com" scope=sub schemachecking=on type=refreshAndPersist retry="30 5 300 3" timeout=1
-
add: olcMirrorMode
olcMirrorMode: TRUE
EOF

# admin_password를 실제 패스워드로 변경 후 실행
sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f multimaster_repl1.ldif

# 적용 후 변경 확인 명령
sudo ldapsearch -Y EXTERNAL -H ldapi:/// -b cn=config "(olcSyncRepl=*)"
# 위 명령 실행 후 결과가 보이지 않으면 ldapmodify 명령 실패 임.
```
### Master2에서 실행
```bash
cat > multimaster_repl2.ldif << 'EOF'
dn: olcDatabase={1}mdb,cn=config
changetype: modify
add: olcSyncRepl
olcSyncRepl: {0}rid=001 provider=ldap://192.168.100.10 binddn="cn=admin,dc=ldap,dc=smartcoreinc,dc=com" bindmethod=simple credentials=core searchbase="dc=ldap,dc=smartcoreinc,dc=com" scope=sub schemachecking=on type=refreshAndPersist retry="30 5 300 3" timeout=1
olcSyncRepl: {1}rid=002 provider=ldap://192.168.100.11 binddn="cn=admin,dc=ldap,dc=smartcoreinc,dc=com" bindmethod=simple credentials=core searchbase="dc=ldap,dc=smartcoreinc,dc=com" scope=sub schemachecking=on type=refreshAndPersist retry="30 5 300 3" timeout=1
-
add: olcMirrorMode
olcMirrorMode: TRUE
EOF

# admin_password를 실제 패스워드로 변경 후 실행
sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f multimaster_repl2.ldif

# 적용 후 변경 확인 명령
sudo ldapsearch -Y EXTERNAL -H ldapi:/// -b cn=config "(olcSyncRepl=*)"
# 위 명령 실행 후 결과가 보이지 않으면 ldapmodify 명령 실패 임.
```
## 7. 서비스 재 시작 및 확인
```bash

# 양쪽 서버에서 실행
sudo systemctl restart slapd
sudo systemctl status slapd

# 로그 확인
sudo tail -f /var/log/syslog | grep slapd
```

## 8. 복제 테스트
### Master1에서 테스트 사용자 추가
```bash

cat > test_user.ldif << 'EOF'
dn: cn=testuser,ou=people,dc=ldap,dc=smartcoreinc,dc=com
objectClass: inetOrgPerson
objectClass: posixAccount
objectClass: shadowAccount
cn: testuser
sn: Test
uid: testuser
uidNumber: 1001
gidNumber: 1001
homeDirectory: /home/testuser
loginShell: /bin/bash
userPassword: {SSHA}generated_password_hash
EOF

ldapadd -x -D "cn=admin,dc=example,dc=com" -W -f test_user.ldif -H ldap://192.168.100.10
```
### Master2에서 복제 확인
```bash
# Master2에서 추가된 사용자 검색
ldapsearch -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -W -H ldap://192.168.100.11 -b "dc=ldap,dc=smartcoreinc,dc=com" "(cn=testuser)"
```

### Master2에서 사용자 수정 후 Master1에서 확인
```bash
# Master2에서 사용자 수정
cat > modify_user.ldif << 'EOF'
dn: cn=testuser,ou=people,dc=ldap,dc=smartcoreinc,dc=com
changetype: modify
replace: sn
sn: TestModified
EOF

ldapmodify -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -W -f modify_user.ldif -H ldap://192.168.100.11

# Master1에서 변경사항 확인
ldapsearch -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -W -H ldap://192.168.100.10 -b "dc=ldap,dc=smartcoreinc,dc=com" "(cn=testuser)"
```

## 문제 해결: ServerID 오류

### 1. 현재 설정 확인

```bash
# ServerID 설정 확인
sudo ldapsearch -Y EXTERNAL -H ldapi:/// -b "cn=config" "(objectClass=olcGlobal)" olcServerID

# slapd 프로세스 확인
ps aux | grep slapd
# 서비스 상태 확인
sudo systemctl status slapd -l
```

### 2. 단계별 해결 방법
**Step 1: slapd 서비스 중지**
```bash
sudo systemctl stop slapd
```
**Step 2: 기존 ServerID 삭제 (필요시)**
```bash
cat > delete_serverid.ldif << 'EOF'
dn: cn=config
changetype: modify
delete: olcServerID
EOF

# slapd를 일시적으로 시작해서 설정 삭제
sudo slapd -h "ldapi:///" -F /etc/ldap/slapd.d &
SLAPD_PID=$!
sleep 2
sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f delete_serverid.ldif 2>/dev/null || true
sudo kill $SLAPD_PID
```
**Step 3: 올바른 ServerID 설정**
```bash
# Master1에서 (ServerID 1)
cat > new_serverid.ldif << 'EOF'
dn: cn=config
changetype: modify
add: olcServerID
olcServerID: 1
EOF

# Master2에서 (ServerID 2)
cat > new_serverid.ldif << 'EOF'
dn: cn=config
changetype: modify
add: olcServerID
olcServerID: 2
EOF

# 설정 적용
sudo slapd -h "ldapi:///" -F /etc/ldap/slapd.d &
SLAPD_PID=$!
sleep 2
sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f new_serverid.ldif
sudo kill $SLAPD_PID
```

**Step 4: 서비스 재시작**
```bash
sudo systemctl start slapd
sudo systemctl status slapd
```

### 복제 상태 확인
```bash
# 복제 상태 모니터링
ldapsearch -Y EXTERNAL -H ldapi:/// -b "cn=config" "(objectClass=olcDatabaseConfig)" olcSyncrepl

# 컨텍스트 CSN 확인 (양쪽 서버에서 비교)
ldapsearch -x -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" -W -H ldap://localhost -b "dc=ldap,dc=smartcoreinc,dc=com" -s base contextCSN

# 여러 번의 추가/삭제 테스트 등으로 contextCSN 이 맞지 않아 reset 해야한 경우
# 1) ldap 서버 종료
sudo systemctl stop slapd
# 2) 기존 데이터베이스 삭제
sudo rm -rf /var/lib/ldap/*
# 3) ldap 서버 재 시작
sudo systemctl start slapd
# 4) 다른 서버로 부터 제대로 복제가 되는지 확인
```

### 일반적인 문제 해결
1. **방화벽 확인**: 포트 389가 열려있는지 확인
2. **시간 동기화**: NTP로 양쪽 서버 시간 동기화
3. **로그 확인**: `/var/log/syslog`에서 slapd 관련 에러 확인
4. **네트워크 연결**: `telnet 192.168.100.10 389` 로 연결 테스트

## 10. 추가 설정 권장사항

### 인덱스 설정 (성능 향상)
```bash
cat > index_config.ldif << 'EOF'
dn: olcDatabase={1}mdb,cn=config
changetype: modify
add: olcDbIndex
olcDbIndex: uid eq
olcDbIndex: cn eq
olcDbIndex: entryCSN eq
olcDbIndex: entryUUID eq
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f index_config.ldif
```

### 로그 레벨 설정 (디버깅 용)
```bash
cat > loglevel.ldif << 'EOF'
dn: cn=config
changetype: modify
replace: olcLogLevel
olcLogLevel: sync stats
EOF

sudo ldapmodify -Y EXTERNAL -H ldapi:/// -f loglevel.ldif
```

## 11. Custom Schema 등록
### 1) pkdDownload
``` shell
cat > pkdDownload.ldif << 'EOF'
olcAttributeTypes: ( 1.3.6.1.4.1.42.2.27.9.1.512 NAME 'pkdVersion' DESC 'PKD Version of the object' EQUALITY integerMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.27 SINGLE-VALUE )
olcAttributeTypes: ( 1.3.6.1.4.1.42.2.27.9.1.513 NAME 'pkdConformanceCode' DESC 'PKD Conformance Code' EQUALITY caseExactMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )
olcAttributeTypes: ( 1.3.6.1.4.1.42.2.27.9.1.514 NAME 'pkdConformanceText' DESC 'PKD Conformance Text' EQUALITY caseExactMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )
olcAttributeTypes: ( 1.3.6.1.4.1.42.2.27.9.1.515 NAME 'pkdConformancePolicy' DESC 'PKD Conformance Policy' EQUALITY caseExactMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 SINGLE-VALUE )
olcObjectClasses: ( 1.3.6.1.4.1.42.2.27.9.2.512 NAME 'pkdDownload' DESC 'PKD Download object' AUXILIARY MAY ( pkdVersion $ pkdConformanceCode $ pkdConformanceText $ pkdConformancePolicy ) )
EOF

# add schema
ldapadd -Y EXTERNAL -H ldapi:/// -f pkdDownload-schema.ldif

# schema 추가 확인
ldapsearch -Y EXTERNAL -H ldapi:/// -b "cn=schema,cn=config" -s one
```
### 2) pkdMasterList
```bash
cat > pkd-masterlist-schema.ldif << 'EOF'
# pkd-masterlist-schema.ldif (for cn=config). REPLACE OIDs before use.
dn: cn=pkd-masterlist,cn=schema,cn=config
objectClass: olcSchemaConfig
cn: pkd-masterlist
olcAttributeTypes: ( 1.3.6.1.4.1.55555.1.1 NAME 'pkdMasterListContent' DESC 'Binary CMS/CSCA Master List content (DER or base64) from ICAO PKD' EQUALITY octetStringMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.40 SINGLE-VALUE )
olcObjectClasses: ( 1.3.6.1.4.1.55555.2.1 NAME 'pkdMasterList' DESC 'Auxiliary class for ICAO PKD Master List entries' AUXILIARY MAY ( pkdMasterListContent ) )
EOF

# add schema
ldapadd -Y EXTERNAL -H ldapi:/// -f pkd-masterlist-schema.ldif

# schema 추가 확인
ldapsearch -Y EXTERNAL -H ldapi:/// -b "cn=schema,cn=config" -s one
```

### 3) CSCA
```bash
cat > csca-schema.ldif << 'EOF'
# csca-schema.ldif (for cn=config). REPLACE OIDs before use.
dn: cn=csca,cn=schema,cn=config
objectClass: olcSchemaConfig
cn: csca
olcAttributeTypes: ( 1.3.6.1.4.1.99999.1.1 NAME 'countryCode'
  DESC 'ISO 3166-1 alpha-2 country code'
  EQUALITY caseIgnoreMatch
  SUBSTR caseIgnoreSubstringsMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.15
  SINGLE-VALUE )
olcAttributeTypes: ( 1.3.6.1.4.1.99999.1.2 NAME 'cscaFingerprint'
  DESC 'SHA-256 fingerprint of CSCA certificate'
  EQUALITY caseIgnoreMatch
  SUBSTR caseIgnoreSubstringsMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.15 )
olcAttributeTypes: ( 1.3.6.1.4.1.99999.1.3 NAME 'issuer'
  DESC 'Issuer DN of the certificate'
  EQUALITY distinguishedNameMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.12 SINGLE-VALUE )
olcAttributeTypes: ( 1.3.6.1.4.1.99999.1.4 NAME 'cscaValidity'
  DESC 'Certificate validity status (valid, expired, revoked)'
  EQUALITY caseIgnoreMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.15
  SINGLE-VALUE )
olcAttributeTypes: ( 1.3.6.1.4.1.99999.1.5 NAME 'notBefore'
  DESC 'Certificate notBefore date (GeneralizedTime)'
  EQUALITY generalizedTimeMatch
  ORDERING generalizedTimeOrderingMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.24
  SINGLE-VALUE )
olcAttributeTypes: ( 1.3.6.1.4.1.99999.1.6 NAME 'notAfter'
  DESC 'Certificate notAfter date (GeneralizedTime)'
  EQUALITY generalizedTimeMatch
  ORDERING generalizedTimeOrderingMatch
  SYNTAX 1.3.6.1.4.1.1466.115.121.1.24
  SINGLE-VALUE )
olcObjectClasses: ( 1.3.6.1.4.1.99999.2.1 NAME 'cscaCertificateObject'
  DESC 'ObjectClass for CSCA Certificate entry'
  SUP top AUXILIARY
  MUST (countryCode $ cscaFingerprint )
  MAY (issuer $ cscaValidity $ notBefore $ notAfter ) )
EOF
  
# add schema
ldapadd -Y EXTERNAL -H ldapi:/// -f csca-schema.ldif

# schema 추가 확인
ldapsearch -Y EXTERNAL -H ldapi:/// -b "cn=schema,cn=config" -s one
```
## 주의 사항

1. **패스워드 보안**: 실제 환경에서는 LDIF 파일의 패스워드를 안전하게 관리하세요.
2. **백업**: 설정 전 기존 데이터를 반드시 백업하세요.
3. **네트워크**: 두 서버 간 네트워크 연결이 안정적이어야 합니다.
4. **시간 동기화**: NTP를 사용하여 시간을 동기화 하세요.
5. **모니터링**: 정기적으로 복제 상태를 확인하세요.