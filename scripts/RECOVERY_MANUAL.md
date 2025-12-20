# 🚨 LDAP Base DN 삭제 복구 매뉴얼

**작성일**: 2025-12-05  
**대상**: 실수로 LDAP base DN을 삭제한 경우  
**소요 시간**: 약 2분

---

## ⚡ 빠른 복구 (Quick Recovery)

### 방법 1: 자동 스크립트 (가장 빠름) ⭐

```bash
cd /home/kbjung/projects/java/smartcore/local-pkd
./scripts/restore-ldap.sh
```

비밀번호 입력 프롬프트가 나오면: `core` 입력

### 방법 2: 직접 명령어 실행

```bash
cd /home/kbjung/projects/java/smartcore/local-pkd

ldapadd -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -f scripts/restore-base-dn.ldif
```

---

## 📋 상세 복구 가이드

### 1단계: 문제 확인

**증상**:
- Apache Directory Studio에서 base DN이 사라짐
- 애플리케이션에서 LDAP 연결 오류 발생
- "No such object (32)" 에러 메시지

**확인 명령어**:
```bash
ldapsearch -x \
    -H ldap://192.168.100.10:389 \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -s base \
    "(objectClass=*)" dn
```

결과가 `result: 32 No such object`이면 복구 필요!

---

### 2단계: 복구 실행

#### Option A: 자동 스크립트 (권장)

```bash
# 1. 프로젝트 디렉토리로 이동
cd /home/kbjung/projects/java/smartcore/local-pkd

# 2. 복구 스크립트 실행
./scripts/restore-ldap.sh

# 3. 비밀번호 입력 (프롬프트가 나오면)
Password: core
```

**예상 출력**:
```
✅ Base DN restoration completed successfully!
📊 Verifying structure...
dn: dc=ldap,dc=smartcoreinc,dc=com
dn: dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
dn: dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
dn: dc=data,dc=download,dc=pkd,dc=smartcoreinc,dc=com
dn: dc=nc-data,dc=download,dc=pkd,dc=smartcoreinc,dc=com
```

#### Option B: 수동 복구

```bash
# 한 줄 명령어로 복구
cd /home/kbjung/projects/java/smartcore/local-pkd && \
ldapadd -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -f scripts/restore-base-dn.ldif
```

#### Option C: Apache Directory Studio 사용

1. LDAP 서버 연결 (192.168.100.10:389)
2. 상단 메뉴: **LDIF** → **Import LDIF...**
3. 파일 선택: `/home/kbjung/projects/java/smartcore/local-pkd/scripts/restore-base-dn.ldif`
4. **Finish** 클릭
5. F5 눌러 새로고침

---

### 3단계: 복구 확인

#### CLI 확인:
```bash
ldapsearch -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL \
    "(objectClass=*)" dn
```

**정상 출력 (5개 엔트리)**:
```
dn: dc=ldap,dc=smartcoreinc,dc=com
dn: dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
dn: dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
dn: dc=data,dc=download,dc=pkd,dc=smartcoreinc,dc=com
dn: dc=nc-data,dc=download,dc=pkd,dc=smartcoreinc,dc=com
```

#### Apache Directory Studio 확인:
1. **F5** 키로 DIT 새로고침
2. Base DN 트리가 보이는지 확인
3. 각 계층이 정상적으로 펼쳐지는지 확인

---

### 4단계: 애플리케이션 테스트

```bash
# 애플리케이션 실행
cd /home/kbjung/projects/java/smartcore/local-pkd
./mvnw spring-boot:run
```

**브라우저 테스트**:
1. `http://172.x.x.x:8081/file/upload` 접속
2. 테스트 LDIF 파일 업로드
3. SSE 진행 상황 확인
4. Apache Directory Studio에서 인증서가 정상 저장되었는지 확인

---

## 🔧 문제 해결 (Troubleshooting)

### ❌ Error: "Already exists (68)"

**원인**: 엔트리가 이미 존재함

**해결**:
```bash
# 기존 엔트리 삭제 후 재시도
ldapdelete -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    "dc=ldap,dc=smartcoreinc,dc=com"

# 그 다음 복구 스크립트 재실행
./scripts/restore-ldap.sh
```

---

### ❌ Error: "Invalid credentials (49)"

**원인**: 잘못된 admin 비밀번호

**해결**:
1. 비밀번호 확인: `core`
2. Bind DN 확인: `cn=admin,dc=ldap,dc=smartcoreinc,dc=com`
3. 필요시 LDAP 관리자에게 문의

---

### ❌ Error: "Can't contact LDAP server (-1)"

**원인**: LDAP 서버 연결 불가

**해결**:
```bash
# 1. LDAP 서버 상태 확인
systemctl status slapd

# 2. 네트워크 연결 확인
telnet 192.168.100.10 389
# 또는
nc -zv 192.168.100.10 389

# 3. 방화벽 확인
sudo firewall-cmd --list-ports | grep 389

# 4. LDAP 서버 재시작 (필요시)
sudo systemctl restart slapd
```

---

### ❌ Error: "No such object (32)" (복구 후에도 발생)

**원인**: 부분적으로만 복구됨

**해결**:
```bash
# 1. 현재 상태 확인
ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL \
    "(objectClass=*)" dn

# 2. 누락된 엔트리 확인 후 LDIF 파일 재검토

# 3. 완전 삭제 후 재생성
# (주의: 모든 데이터 삭제됨!)
ldapdelete -r -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    "dc=ldap,dc=smartcoreinc,dc=com"

# 4. 복구 재실행
./scripts/restore-ldap.sh
```

---

## 💾 백업 방법 (예방이 최선!)

### 정기 백업 생성

```bash
# 전체 LDAP 백업
slapcat -b "dc=ldap,dc=smartcoreinc,dc=com" \
    > ~/ldap-backups/backup-$(date +%Y%m%d-%H%M%S).ldif

# 또는 ldapsearch 사용
ldapsearch -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL \
    "(objectClass=*)" \
    > ~/ldap-backups/backup-$(date +%Y%m%d-%H%M%S).ldif
```

### 백업에서 복구

```bash
# 백업 파일 확인
ls -lh ~/ldap-backups/

# 백업에서 복구
ldapadd -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -f ~/ldap-backups/backup-20251205-143000.ldif
```

### 자동 백업 스크립트 (선택사항)

```bash
#!/bin/bash
# ~/cron-ldap-backup.sh

BACKUP_DIR="$HOME/ldap-backups"
mkdir -p "$BACKUP_DIR"

DATE=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="$BACKUP_DIR/ldap-backup-$DATE.ldif"

ldapsearch -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL \
    "(objectClass=*)" \
    > "$BACKUP_FILE"

# 7일 이상 된 백업 삭제
find "$BACKUP_DIR" -name "ldap-backup-*.ldif" -mtime +7 -delete

echo "Backup completed: $BACKUP_FILE"
```

**Cron 설정 (매일 새벽 3시)**:
```bash
# crontab -e
0 3 * * * /home/kbjung/cron-ldap-backup.sh >> /home/kbjung/ldap-backup.log 2>&1
```

---

## 🛡️ 실수 예방 팁

### Apache Directory Studio 사용 시:

1. **삭제 전 확인 습관화**:
   - 삭제하려는 DN을 두 번 확인
   - 중요한 노드는 백업 후 삭제

2. **읽기 전용 연결 사용**:
   - 조회용 계정 별도 생성
   - 수정 작업은 별도 연결 사용

3. **확인 대화상자 활성화**:
   - Preferences → LDAP Browser → Confirmation
   - "Ask for confirmation on delete" 체크

4. **Base DN에는 잠금 설정** (가능한 경우):
   - LDAP ACL로 실수 방지

### 개발 환경과 운영 환경 분리:

```bash
# 개발용 LDAP (로컬)
ldap://localhost:389

# 운영용 LDAP (원격)
ldap://192.168.100.10:389
```

- **개발**: 로컬 Docker/Podman LDAP 사용
- **운영**: 원격 LDAP는 신중하게 접근

---

## 📂 관련 파일

| 파일 | 용도 |
|------|------|
| `scripts/restore-base-dn.ldif` | Base DN 복구용 LDIF 파일 |
| `scripts/restore-ldap.sh` | 자동 복구 스크립트 |
| `docs/LDAP_BASE_DN_RECOVERY.md` | 상세 기술 문서 |
| `scripts/RECOVERY_MANUAL.md` | 이 매뉴얼 |

---

## ⚠️ 중요 주의사항

1. **데이터 손실 불가피**:
   - Base DN 삭제 시 하위 모든 데이터 삭제됨
   - 복구 후 인증서는 재업로드 필요

2. **복구 후 작업**:
   - 애플리케이션 재시작 권장
   - LDIF/Master List 파일 재업로드
   - 데이터 정합성 확인

3. **보안**:
   - 비밀번호(`core`)는 예제용
   - 운영 환경에서는 강력한 비밀번호 사용
   - 백업 파일 접근 권한 제한

---

## 📞 추가 지원

**문제가 해결되지 않을 경우**:

1. LDAP 서버 로그 확인:
   ```bash
   sudo tail -f /var/log/slapd/slapd.log
   ```

2. 상세 기술 문서 참조:
   - [docs/LDAP_BASE_DN_RECOVERY.md](../docs/LDAP_BASE_DN_RECOVERY.md)
   - [CLAUDE.md](../CLAUDE.md) - Section: LDAP DIT Structure

3. 프로젝트 문서:
   - [docs/PROJECT_SUMMARY_2025-11-21.md](../docs/PROJECT_SUMMARY_2025-11-21.md)

---

## 📝 체크리스트

복구 완료 후 다음 항목을 확인하세요:

- [ ] LDAP 서버 연결 정상
- [ ] Base DN 존재 확인 (5개 엔트리)
- [ ] Apache Directory Studio 정상 표시
- [ ] 애플리케이션 실행 정상
- [ ] 테스트 파일 업로드 성공
- [ ] 백업 생성 (향후 대비)

---

**문서 버전**: 1.0  
**최종 업데이트**: 2025-12-05  
**작성자**: kbjung (with Claude assistance)  
**상태**: 실전 테스트 완료 ✅

---

## 🎓 참고: LDIF 파일 구조

복구되는 구조를 이해하면 향후 문제 해결에 도움이 됩니다:

```ldif
# 1. Base DN (최상위)
dn: dc=ldap,dc=smartcoreinc,dc=com
objectClass: top
objectClass: domain
dc: ldap

# 2. PKD Layer
dn: dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: top
objectClass: domain
dc: pkd

# 3. Download Layer
dn: dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: top
objectClass: domain
dc: download

# 4. Data Layer (표준 데이터)
dn: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: top
objectClass: domain
dc: data

# 5. Non-Conformant Data Layer (비표준 데이터)
dn: dc=nc-data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
objectClass: top
objectClass: domain
dc: nc-data
```

**계층 구조**:
```
dc=ldap,dc=smartcoreinc,dc=com
└── dc=pkd
    └── dc=download
        ├── dc=data (표준)
        │   ├── c=KR, o=csca (한국 CSCA)
        │   ├── c=KR, o=dsc (한국 DSC)
        │   ├── c=KR, o=ml (한국 Master List)
        │   └── c=KR, o=crl (한국 CRL)
        └── dc=nc-data (비표준)
            └── c=XX, o=dsc (비표준 DSC)
```

---

**매뉴얼 끝** 🎉
