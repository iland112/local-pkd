# Data Verification Script

## Overview

The `verify-upload-data.sh` script automates the verification of ICAO PKD upload data by comparing PostgreSQL database contents with LDAP server entries.

**Version**: 1.0
**Date**: 2025-12-11
**Author**: Claude (Anthropic) & SmartCore Inc.

---

## Features

- ✅ **Database Statistics**: Count certificates, CRLs, and validation status
- ✅ **LDAP Statistics**: Count LDAP entries
- ✅ **Country Code Validation**: Check for null country codes (should be 0%)
- ✅ **Data Comparison**: Compare database vs LDAP counts
- ✅ **Upload Flag Verification**: Check `uploaded_to_ldap` flag status
- ✅ **Country Distribution**: Display top 10 countries by certificate count
- ✅ **JSON Output**: Machine-readable output for automation

---

## Requirements

- `psql` (PostgreSQL client)
- `ldapsearch` (OpenLDAP client)
- `bc` (Basic calculator for percentage calculations)
- `jq` (Optional, for pretty-printing JSON output)

---

## Usage

### Basic Usage

```bash
./scripts/verify-upload-data.sh
```

### Verbose Output

```bash
./scripts/verify-upload-data.sh --verbose
```

### JSON Output

```bash
./scripts/verify-upload-data.sh --json
```

### Pretty JSON Output

```bash
./scripts/verify-upload-data.sh --json | jq .
```

### Help

```bash
./scripts/verify-upload-data.sh --help
```

---

## Configuration

### Environment Variables

You can customize the script behavior using environment variables:

#### PostgreSQL

```bash
export PGHOST=localhost         # PostgreSQL host (default: localhost)
export PGPORT=5432             # PostgreSQL port (default: 5432)
export PGUSER=postgres         # PostgreSQL user (default: postgres)
export PGPASSWORD=secret       # PostgreSQL password (default: secret)
export PGDATABASE=icao_local_pkd  # Database name (default: icao_local_pkd)
```

#### LDAP

```bash
export LDAP_HOST=192.168.100.10  # LDAP host (default: 192.168.100.10)
export LDAP_PORT=389             # LDAP port (default: 389)
export LDAP_BIND_DN="cn=admin,dc=ldap,dc=smartcoreinc,dc=com"  # Bind DN
export LDAP_PASSWORD=core        # LDAP password (default: core)
export LDAP_BASE_DN="dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com"  # Base DN
```

### Example with Custom Configuration

```bash
PGHOST=prod-db.example.com \
LDAP_HOST=prod-ldap.example.com \
./scripts/verify-upload-data.sh
```

---

## Output Format

### Standard Output

The script provides color-coded output with the following sections:

1. **Database Certificates**
   - Total certificates
   - CSCA count
   - DSC count
   - Null country code count

2. **Database CRLs**
   - Total CRL count

3. **Validation Status**
   - Valid certificates (count & percentage)
   - Invalid certificates (count & percentage)
   - Expired certificates (count & percentage)

4. **LDAP Certificates**
   - Total LDAP entries
   - Approximate CSCA count
   - Approximate DSC count

5. **LDAP CRLs**
   - Total CRL entries

6. **Uploaded to LDAP Flags**
   - Certificates with `uploaded_to_ldap = true`
   - Certificates with `uploaded_to_ldap = false`

7. **Database vs LDAP Comparison**
   - Certificate difference
   - CRL difference

8. **Top 10 Country Distribution**
   - Country code and certificate count

9. **Verification Summary**
   - Overall PASS/FAIL status

### JSON Output

```json
{
  "timestamp": "2025-12-11T10:57:02Z",
  "database": {
    "certificates": {
      "total": 29959,
      "csca": 520,
      "dsc": 29439,
      "null_country_code": 0
    },
    "crls": 67,
    "validation": {
      "valid": 3368,
      "invalid": 23390,
      "expired": 3201
    },
    "uploaded_flags": {
      "true": 0,
      "false": 29959
    }
  },
  "ldap": {
    "certificates": 29913,
    "crls": 67
  },
  "comparison": {
    "certificates_diff": 46,
    "crls_diff": 0
  },
  "success": true
}
```

---

## Exit Codes

- **0**: Success (all verifications passed)
- **1**: Error (verification failed or command error)

### Success Criteria

- Certificate difference < 50 (less than 0.2% missing)
- CRL difference = 0 (perfect match)

---

## Integration with CI/CD

### Example: Jenkins Pipeline

```groovy
stage('Verify Upload Data') {
    steps {
        sh '''
            ./scripts/verify-upload-data.sh --json > verification-report.json
            if [ $? -ne 0 ]; then
                echo "Verification failed!"
                exit 1
            fi
        '''
        archiveArtifacts artifacts: 'verification-report.json'
    }
}
```

### Example: GitHub Actions

```yaml
- name: Verify Upload Data
  run: |
    ./scripts/verify-upload-data.sh --json | tee verification-report.json

- name: Upload Verification Report
  uses: actions/upload-artifact@v3
  with:
    name: verification-report
    path: verification-report.json
```

---

## Troubleshooting

### psql: connection refused

**Problem**: Cannot connect to PostgreSQL database.

**Solution**:
```bash
# Check PostgreSQL is running
sudo systemctl status postgresql

# Check connection parameters
export PGHOST=localhost
export PGPORT=5432
./scripts/verify-upload-data.sh --verbose
```

### ldapsearch: connection refused

**Problem**: Cannot connect to LDAP server.

**Solution**:
```bash
# Check LDAP server is accessible
ldapsearch -x -H ldap://192.168.100.10:389 -b "" -s base "(objectClass=*)"

# Check LDAP configuration
export LDAP_HOST=192.168.100.10
export LDAP_PORT=389
./scripts/verify-upload-data.sh --verbose
```

### bc: command not found

**Problem**: Basic calculator not installed.

**Solution**:
```bash
# Ubuntu/Debian
sudo apt-get install bc

# CentOS/RHEL
sudo yum install bc
```

---

## Maintenance

### Adding New Verifications

To add a new verification function:

1. Create a new function in the script:
   ```bash
   verify_new_feature() {
       print_section "New Feature Verification"

       local result=$(psql_count "SELECT COUNT(*) FROM new_table;")

       print_info "Result: $result"

       echo "$result"
   }
   ```

2. Call the function in `main()`:
   ```bash
   new_feature_result=$(verify_new_feature)
   ```

3. Update JSON output if needed.

### Customizing Success Criteria

Modify the comparison in `compare_db_ldap()`:

```bash
# Current: diff < 50
if [[ "$diff_certs" -lt 50 ]]; then

# Custom: diff < 100
if [[ "$diff_certs" -lt 100 ]]; then
```

---

## Related Documentation

- [CLAUDE.md](../CLAUDE.md) - Project development guide
- [SESSION_2025-12-11_CRL_PERSISTENCE_AND_UI_FIXES.md](../docs/SESSION_2025-12-11_CRL_PERSISTENCE_AND_UI_FIXES.md) - Recent bug fixes
- [PROJECT_SUMMARY_2025-11-21.md](../docs/PROJECT_SUMMARY_2025-11-21.md) - Project overview

---

## Changelog

### Version 1.0 (2025-12-11)

- Initial release
- Database certificate/CRL verification
- LDAP entry verification
- Country code validation
- Upload flag verification
- JSON output support
- Verbose logging
- Color-coded output

---

## License

This script is part of the Local PKD Evaluation Project.

Copyright © 2025 SmartCore Inc. All rights reserved.
