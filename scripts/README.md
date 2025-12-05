# Scripts Directory

이 디렉토리에는 프로젝트 관리 및 복구 스크립트가 포함되어 있습니다.

---

## 📁 파일 목록

### LDAP 복구 관련

| 파일 | 용도 | 사용 빈도 |
|------|------|-----------|
| **[QUICK_RECOVERY.txt](QUICK_RECOVERY.txt)** | 긴급 복구 Quick Reference (30초 복구) | 🚨 긴급 시 |
| **[RECOVERY_MANUAL.md](RECOVERY_MANUAL.md)** | 상세 복구 매뉴얼 (문제 해결, 백업) | 📖 필독 |
| **[restore-ldap.sh](restore-ldap.sh)** | 자동 복구 스크립트 (실행 가능) | ⚡ 자주 사용 |
| **[restore-base-dn.ldif](restore-base-dn.ldif)** | Base DN 구조 LDIF 파일 | 📋 참조용 |

### 컨테이너 관리 (프로젝트 루트)

| 파일 | 용도 |
|------|------|
| `../podman-start.sh` | PostgreSQL 컨테이너 시작 |
| `../podman-stop.sh` | PostgreSQL 컨테이너 중지 |
| `../podman-restart.sh` | PostgreSQL 컨테이너 재시작 |
| `../podman-clean.sh` | 컨테이너 완전 삭제 및 초기화 |

---

## 🚨 긴급 상황: LDAP Base DN 삭제됨!

### 빠른 복구 (30초)

```bash
cd /home/kbjung/projects/java/smartcore/local-pkd
./scripts/restore-ldap.sh
# 비밀번호 입력: core
```

### 더 자세한 내용은?

1. **빠른 참조**: [QUICK_RECOVERY.txt](QUICK_RECOVERY.txt) - 한 페이지 요약
2. **상세 가이드**: [RECOVERY_MANUAL.md](RECOVERY_MANUAL.md) - 전체 매뉴얼
3. **기술 문서**: [../docs/LDAP_BASE_DN_RECOVERY.md](../docs/LDAP_BASE_DN_RECOVERY.md)

---

## 📚 사용 예시

### 1. LDAP Base DN 복구

```bash
# 자동 스크립트 실행
./restore-ldap.sh

# 또는 직접 명령어
ldapadd -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -f restore-base-dn.ldif
```

### 2. LDAP 상태 확인

```bash
# Base DN 존재 확인
ldapsearch -x -H ldap://192.168.100.10:389 \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -s base "(objectClass=*)" dn

# 전체 구조 확인
ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL "(objectClass=*)" dn
```

### 3. LDAP 백업 생성

```bash
# 백업 디렉토리 생성
mkdir -p ~/ldap-backups

# 전체 백업
ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL "(objectClass=*)" \
    > ~/ldap-backups/backup-$(date +%Y%m%d-%H%M%S).ldif
```

---

## ⚠️ 중요 참고사항

### 보안

- 이 스크립트들은 개발/테스트 환경용입니다
- 운영 환경에서는 비밀번호를 안전하게 관리하세요
- 백업 파일 접근 권한을 제한하세요

### 데이터 손실

- Base DN 복구는 구조만 복원합니다
- 삭제된 인증서 데이터는 복구되지 않습니다
- 정기적인 백업이 필수입니다!

---

## 🔗 관련 문서

- [CLAUDE.md](../CLAUDE.md) - 프로젝트 전체 가이드
- [docs/LDAP_BASE_DN_RECOVERY.md](../docs/LDAP_BASE_DN_RECOVERY.md) - LDAP 복구 기술 문서
- [docs/PROJECT_SUMMARY_2025-11-21.md](../docs/PROJECT_SUMMARY_2025-11-21.md) - 프로젝트 요약

---

**최종 업데이트**: 2025-12-05  
**상태**: 실전 테스트 완료 ✅
