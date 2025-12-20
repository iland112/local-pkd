#!/bin/bash

# LDAP Base DN Restoration Script
# Date: 2025-12-05
# Purpose: Restore deleted base DN to OpenLDAP server

set -e

# LDAP Server Configuration
LDAP_HOST="192.168.100.10"
LDAP_PORT="389"
LDAP_BIND_DN="cn=admin,dc=ldap,dc=smartcoreinc,dc=com"
LDAP_PASSWORD="your_admin_password_here"  # ⚠️ CHANGE THIS!

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LDIF_FILE="$SCRIPT_DIR/restore-base-dn.ldif"

echo "============================================"
echo "LDAP Base DN Restoration"
echo "============================================"
echo "Server: $LDAP_HOST:$LDAP_PORT"
echo "LDIF File: $LDIF_FILE"
echo ""

# Check if LDIF file exists
if [ ! -f "$LDIF_FILE" ]; then
    echo "❌ Error: LDIF file not found: $LDIF_FILE"
    exit 1
fi

echo "📋 LDIF File Contents:"
echo "--------------------------------------------"
cat "$LDIF_FILE"
echo "--------------------------------------------"
echo ""

# Prompt for password if not set
if [ "$LDAP_PASSWORD" == "your_admin_password_here" ]; then
    echo "⚠️  Please enter LDAP admin password:"
    read -s LDAP_PASSWORD
    echo ""
fi

# Add entries to LDAP
echo "🚀 Adding entries to LDAP server..."
ldapadd -x \
    -H "ldap://$LDAP_HOST:$LDAP_PORT" \
    -D "$LDAP_BIND_DN" \
    -w "$LDAP_PASSWORD" \
    -f "$LDIF_FILE"

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Base DN restoration completed successfully!"
    echo ""
    echo "📊 Verifying structure..."
    ldapsearch -x \
        -H "ldap://$LDAP_HOST:$LDAP_PORT" \
        -D "$LDAP_BIND_DN" \
        -w "$LDAP_PASSWORD" \
        -b "dc=ldap,dc=smartcoreinc,dc=com" \
        -LLL \
        "(objectClass=*)" \
        dn
else
    echo ""
    echo "❌ Error: Failed to restore base DN"
    exit 1
fi
