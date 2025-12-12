# JPA CRL Binary Mapping Issue Fix

**Date**: 2025-12-11
**Issue**: CRL 저장 시 JPA type mismatch 오류 발생
**Error**: `column "crl_binary" is of type bytea but expression is of type bigint`
**Root Cause**: Lombok `@Getter`와 수동 getter 메서드 충돌로 Hibernate가 잘못된 필드 매핑
**Resolution**: `@Getter(AccessLevel.NONE)` 어노테이션 추가하여 Lombok getter 생성 제외

---

## 🚨 문제 발견

### 오류 로그 (log/localpkd.log:179-241)

```
2025-12-11 14:03:57 [ERROR] ERROR: column "crl_binary" is of type bytea but expression is of type bigint
  Hint: You will need to rewrite or cast the expression.

Batch entry 0 insert into certificate_revocation_list
  (country_code,created_at,issuer_name,...,crl_binary,revoked_count,id)
  values (('SK'),...,('98131'::int8),('0'::int4),(...))
                      ↑ 문제!!!
```

### 문제 분석

**Expected** (예상):
```sql
...,crl_binary,revoked_count,id) values (...,(BYTEA_DATA),(0::int4),(...))
                                              ↑ byte[] 배열
```

**Actual** (실제):
```sql
...,crl_binary,revoked_count,id) values (...,(98131::int8),(0::int4),(...))
                                              ↑ 바이너리 크기 (length)
```

**결과**: `crl_binary` 컬럼(BYTEA 타입)에 `98131`이라는 정수값을 넣으려고 시도 → SQL 타입 불일치 오류

**98131의 의미**: CRL 바이너리 데이터의 크기 (`crlBinary.length`)

---

## 🔍 근본 원인 분석

### X509CrlData Value Object 구조

**파일**: `src/main/java/com/smartcoreinc/localpkd/certificatevalidation/domain/model/X509CrlData.java`

**문제가 된 코드**:

```java
@Embeddable
@Getter  // ← Lombok: 모든 필드에 getter 자동 생성
@EqualsAndHashCode
public class X509CrlData implements ValueObject {

    @Lob
    @Column(name = "crl_binary", nullable = false, columnDefinition = "BYTEA")
    private byte[] crlBinary;  // ← Lombok이 getCrlBinary() 자동 생성

    @Column(name = "revoked_count", nullable = false, columnDefinition = "INT DEFAULT 0")
    private int revokedCount;  // ← Lombok이 getRevokedCount() 자동 생성

    // ...

    // 수동 getter 메서드 (복사본 반환)
    public byte[] getCrlBinary() {  // ← Lombok의 자동 생성 getter와 충돌!
        return crlBinary != null ? Arrays.copyOf(crlBinary, crlBinary.length) : null;
    }

    // 크기 반환 메서드
    public int getSize() {  // ← Hibernate가 이것을 crlBinary getter로 오인할 가능성
        return crlBinary != null ? crlBinary.length : 0;
    }
}
```

### 충돌 메커니즘

1. **Lombok `@Getter`**: 클래스 레벨에 선언되어 **모든 필드**에 getter를 자동 생성
   - 자동 생성: `public byte[] getCrlBinary()` (원본 배열 반환)

2. **수동 Getter**: `getCrlBinary()` 메서드를 수동으로 구현 (복사본 반환)
   - 수동 구현: `public byte[] getCrlBinary()` (Arrays.copyOf)

3. **Hibernate 혼란**:
   - Lombok 자동 생성 getter vs 수동 getter 충돌
   - Hibernate가 `getSize()` 메서드를 `crlBinary` 프로퍼티로 오인
   - 또는 Lombok의 자동 생성 getter와 수동 getter 간 충돌로 필드 매핑 실패

4. **결과**:
   - `crl_binary` 컬럼에 `crlBinary.length` (98131) 값이 매핑됨
   - `byte[]` 배열 대신 `int` 값을 BYTEA 컬럼에 삽입 시도
   - PostgreSQL에서 타입 불일치 오류 발생

---

## ✅ 해결 방법

### 수정 사항

**파일**: `X509CrlData.java`

**Before**:
```java
@Lob
@Column(name = "crl_binary", nullable = false, columnDefinition = "BYTEA")
private byte[] crlBinary;
```

**After**:
```java
@Lob
@Column(name = "crl_binary", nullable = false, columnDefinition = "BYTEA")
@lombok.Getter(lombok.AccessLevel.NONE)  // ← Lombok getter 생성 제외
private byte[] crlBinary;
```

### 변경 이유

1. **Lombok getter 생성 제외**: `@Getter(AccessLevel.NONE)` 어노테이션으로 `crlBinary` 필드에 대한 자동 getter 생성 비활성화

2. **수동 getter 유지**: Line 143의 `public byte[] getCrlBinary()` 메서드만 사용
   - 복사본 반환으로 캡슐화 유지
   - Hibernate가 이 메서드만 사용하도록 명확화

3. **충돌 제거**: Lombok 자동 생성 + 수동 구현 충돌 완전 해결

---

## 🧪 검증

### 빌드 결과

```bash
[INFO] BUILD SUCCESS
[INFO] Total time:  9.830 s
[INFO] Compiling 207 source files
```

### 예상 SQL (수정 후)

```sql
insert into certificate_revocation_list
  (...,crl_binary,revoked_count,id)
  values (...,(<BYTEA_BINARY_DATA>),(0::int4),(...))
              ↑ 실제 byte[] 배열 데이터
```

### 테스트 계획

1. ✅ 빌드 성공 확인
2. ⏳ 애플리케이션 재시작
3. ⏳ LDIF 파일 업로드
4. ⏳ CRL 67개 정상 저장 확인
5. ⏳ PostgreSQL에서 `crl_binary` 컬럼 데이터 타입 확인

---

## 📚 Lombok 모범 사례

### 이번 사례에서 배운 교훈

1. **클래스 레벨 `@Getter` 주의**:
   - 모든 필드에 자동 getter 생성
   - 수동 getter 구현 시 충돌 가능성

2. **필드별 제어**:
   ```java
   // ❌ 잘못된 방법
   @Getter
   private byte[] data;
   public byte[] getData() { return Arrays.copyOf(data, data.length); }
   // → Lombok도 getData() 생성, 수동 구현과 충돌!

   // ✅ 올바른 방법
   @Getter(AccessLevel.NONE)  // Lombok getter 제외
   private byte[] data;
   public byte[] getData() { return Arrays.copyOf(data, data.length); }
   // → 수동 getter만 사용, 충돌 없음
   ```

3. **Hibernate/JPA와 Lombok 조합 시 주의사항**:
   - `@Embeddable` Value Object에서 특히 주의
   - 수동 getter가 필요한 경우 명시적으로 Lombok 제외
   - 복잡한 타입 (byte[], List 등) 처리 시 더욱 신중

4. **대안**:
   ```java
   // Option 1: 클래스 레벨에서 특정 필드 제외
   @Getter(exclude = "crlBinary")
   public class X509CrlData { ... }

   // Option 2: 필드 레벨에서 제외
   @Getter(AccessLevel.NONE)
   private byte[] crlBinary;

   // Option 3: 개별 필드에만 @Getter 적용 (클래스 레벨 제거)
   @Getter private int revokedCount;
   private byte[] crlBinary;  // getter 없음 → 수동 구현
   ```

---

## 🔗 관련 이슈 & 문서

### 유사한 Lombok 충돌 사례

**Stack Overflow**: "Hibernate ignores manual getter when Lombok @Getter is present"
- Solution: Use `@Getter(AccessLevel.NONE)` on specific fields

**Spring Data JPA**: Field-level access vs Property-level access
- JPA는 기본적으로 getter 메서드를 통해 프로퍼티에 접근
- Lombok 자동 생성 + 수동 구현 시 어떤 것을 사용할지 불명확

### 참고 문서

- [Lombok @Getter Documentation](https://projectlombok.org/features/GetterSetter)
- [Hibernate Access Type](https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#access)
- [JPA Access Strategies](https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#a121)

---

## 🎯 요약

| 항목 | 내용 |
|------|------|
| **문제** | CRL 바이너리 데이터를 저장할 때 JPA 타입 불일치 오류 |
| **증상** | `bytea` 컬럼에 `bigint` 값 삽입 시도 |
| **원인** | Lombok `@Getter`와 수동 getter 메서드 충돌 |
| **해결** | `@Getter(AccessLevel.NONE)` 어노테이션 추가 |
| **영향** | CRL 저장 실패 → 인증서 검증 불가 (Critical) |
| **수정 위치** | X509CrlData.java Line 73 |
| **수정 코드** | 1 line 추가 |
| **테스트** | 빌드 성공 ✅, 실제 업로드 테스트 필요 ⏳ |

---

**다음 단계**:
1. 애플리케이션 재시작
2. LDIF 파일 업로드 테스트
3. PostgreSQL에서 `SELECT encode(crl_binary, 'hex') FROM certificate_revocation_list LIMIT 1;` 실행하여 바이너리 데이터 저장 확인
4. 67개 CRL 모두 정상 저장되었는지 확인

---

**Document Version**: 1.0
**Author**: Claude (Anthropic)
**Reviewed By**: kbjung
**Status**: ✅ Fix Applied, Awaiting Integration Test
