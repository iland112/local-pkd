# LDAP Base DN Recovery Guide

**Date**: 2025-12-05  
**Issue**: Accidentally deleted base DN `dc=ldap,dc=smartcoreinc,dc=com` from OpenLDAP server  
**Status**: Recovery procedure documented and tested

---

## 🚨 Problem Summary

Base DN and all child entries were deleted from OpenLDAP server (192.168.100.10:389) using Apache Directory Studio.

**Deleted Structure**:
```
dc=ldap,dc=smartcoreinc,dc=com (BASE DN)
└── dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
    └── dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
        ├── dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
        │   ├── o=csca,c={COUNTRY}
        │   ├── o=dsc,c={COUNTRY}
        │   ├── o=ml,c={COUNTRY}
        │   └── o=crl,c={COUNTRY}
        └── dc=nc-data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
            └── o=dsc,c={COUNTRY}
```

---

## ✅ Recovery Methods

### Method 1: Automated Script (Recommended)

**Prerequisites**:
- `ldap-utils` package installed
- LDAP admin credentials
- Network access to 192.168.100.10:389

**Steps**:

1. **Edit admin password** in [scripts/restore-ldap.sh](../scripts/restore-ldap.sh):
   ```bash
   LDAP_PASSWORD="your_actual_admin_password"
   ```

2. **Run restoration script**:
   ```bash
   cd /home/kbjung/projects/java/smartcore/local-pkd
   ./scripts/restore-ldap.sh
   ```

3. **Expected output**:
   ```
   ✅ Base DN restoration completed successfully!
   📊 Verifying structure...
   dn: dc=ldap,dc=smartcoreinc,dc=com
   dn: dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
   dn: dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
   dn: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
   dn: dc=nc-data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com
   ```

---

### Method 2: Manual LDIF Import (Alternative)

**Using ldapadd command**:
```bash
ldapadd -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -W \
    -f scripts/restore-base-dn.ldif
```

**Using Apache Directory Studio**:

1. Connect to LDAP server (192.168.100.10:389)
2. Right-click on Root DSE → Import → LDIF Import
3. Select file: `scripts/restore-base-dn.ldif`
4. Click "Finish"

---

## 📋 LDIF File Details

**File**: [scripts/restore-base-dn.ldif](../scripts/restore-base-dn.ldif)

**Created Entries**:

| DN | ObjectClass | Description |
|----|-------------|-------------|
| `dc=ldap,dc=smartcoreinc,dc=com` | domain | Base DN (Root) |
| `dc=pkd,...` | domain | PKD Layer |
| `dc=download,...` | domain | Download Layer |
| `dc=data,...` | domain | Standard Data Layer |
| `dc=nc-data,...` | domain | Non-Conformant Data Layer |

**Note**: Country-specific organizational units (o=csca, o=dsc, etc.) will be automatically created by the application when uploading certificates.

---

## 🔍 Verification

### Check Base DN exists:
```bash
ldapsearch -x \
    -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -W \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -LLL \
    "(objectClass=*)" \
    dn
```

### Check from Apache Directory Studio:
1. Refresh DIT (F5)
2. Expand tree to verify structure
3. Base DN should appear with all child entries

---

## 🧪 Testing Application Integration

After base DN restoration, test certificate upload:

1. **Start application**:
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Upload test LDIF file**:
   - Navigate to http://172.x.x.x:8081/file/upload
   - Upload sample ICAO PKD LDIF file
   - Monitor SSE progress

3. **Verify LDAP entries**:
   ```bash
   ldapsearch -x \
       -H ldap://192.168.100.10:389 \
       -b "dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com" \
       -LLL \
       "(o=csca)" \
       dn
   ```

---

## ⚠️ Troubleshooting

### Error: "Already exists (68)"
**Cause**: Entry already exists in LDAP  
**Solution**: Skip this entry or delete existing entry first

### Error: "Invalid credentials (49)"
**Cause**: Wrong admin password  
**Solution**: Verify admin password in LDAP configuration

### Error: "Can't contact LDAP server (-1)"
**Cause**: Network issue or LDAP service down  
**Solution**: 
- Check LDAP service status: `systemctl status slapd`
- Verify network connectivity: `telnet 192.168.100.10 389`
- Check firewall rules

### Error: "No such object (32)"
**Cause**: Parent entry does not exist  
**Solution**: Ensure entries are added in hierarchical order (top to bottom)

---

## 🔐 Security Notes

1. **Never commit admin password** to version control
2. Use environment variables or secrets management
3. Restrict LDAP admin access to authorized users only
4. Enable TLS/SSL for production environments (ldaps://)

---

## 📚 Related Documents

- [CLAUDE.md](../CLAUDE.md) - Project overview and LDAP DIT structure
- [PROJECT_SUMMARY_2025-11-21.md](PROJECT_SUMMARY_2025-11-21.md) - LDAP integration details
- [PHASE_17_COMPLETE.md](PHASE_17_COMPLETE.md) - Event-Driven LDAP Upload implementation

---

## 📞 Support

If you encounter issues during base DN recovery:

1. Check LDAP server logs: `/var/log/slapd/slapd.log`
2. Verify LDAP configuration: `/etc/ldap/slapd.d/`
3. Test with `slapcat` command to dump current database

**Contact**: kbjung  
**Project**: SmartCore Local PKD  
**Last Updated**: 2025-12-05
