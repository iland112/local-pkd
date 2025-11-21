#!/bin/bash

##############################################################################
# Reset Test Environment Script
#
# Purpose: Clean PostgreSQL and OpenLDAP for fresh testing
#
# Usage: ./scripts/reset-test-environment.sh
##############################################################################

set -e  # Exit on error

echo "=========================================="
echo " Reset Test Environment"
echo "=========================================="
echo ""

# Get project root directory (parent of scripts directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Stop running application
echo -e "${YELLOW}[1/4] Stopping running applications...${NC}"
lsof -ti:8081 | xargs kill -9 2>/dev/null || true
pkill -9 -f "mvnw" 2>/dev/null || true
sleep 2
echo -e "${GREEN}✓ Applications stopped${NC}"
echo ""

# Clean PostgreSQL (using podman container recreation)
echo -e "${YELLOW}[2/4] Cleaning PostgreSQL database...${NC}"
echo "Stopping and removing PostgreSQL container..."

# Stop and remove containers (bypass confirmation)
cd "${PROJECT_ROOT}" && echo "yes" | ./podman-clean.sh > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ PostgreSQL container removed${NC}"
else
    echo -e "${RED}✗ PostgreSQL container removal failed${NC}"
    exit 1
fi

echo "Recreating PostgreSQL container..."
cd "${PROJECT_ROOT}" && ./podman-start.sh > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓ PostgreSQL container recreated${NC}"
    echo "Waiting for PostgreSQL to be ready..."
    sleep 15
else
    echo -e "${RED}✗ PostgreSQL container creation failed${NC}"
    exit 1
fi
echo ""

# Clean OpenLDAP
echo -e "${YELLOW}[3/4] Cleaning OpenLDAP entries...${NC}"

# Get all child entries under rootDN (excluding rootDN itself)
# Sort in reverse order to delete leaf entries first
CHILD_ENTRIES=$(ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -s one \
    "(objectClass=*)" dn 2>/dev/null | grep "^dn:" | sed 's/^dn: //' || echo "")

# Delete each child entry recursively
if [ ! -z "$CHILD_ENTRIES" ]; then
    echo "Deleting child entries under rootDN..."
    while IFS= read -r entry; do
        if [ ! -z "$entry" ]; then
            echo "  Deleting: $entry"
            ldapdelete -x -H ldap://192.168.100.10:389 \
                -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
                -w "core" \
                -r "$entry" 2>/dev/null || true
        fi
    done <<< "$CHILD_ENTRIES"
else
    echo "No child entries found under rootDN"
fi

# Verify rootDN still exists
ROOTDN_EXISTS=$(ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    -s base \
    "(objectClass=*)" dn 2>/dev/null | grep -c "^dn:" || echo "0")

if [ "$ROOTDN_EXISTS" -eq "1" ]; then
    echo -e "${GREEN}✓ OpenLDAP cleaned (rootDN preserved, child entries deleted)${NC}"
else
    echo -e "${RED}✗ WARNING: rootDN was deleted!${NC}"
fi

# Count remaining entries
ENTRY_COUNT=$(ldapsearch -x -H ldap://192.168.100.10:389 \
    -D "cn=admin,dc=ldap,dc=smartcoreinc,dc=com" \
    -w "core" \
    -b "dc=ldap,dc=smartcoreinc,dc=com" \
    "(objectClass=*)" dn 2>/dev/null | grep -c "^dn:" || echo "0")

echo "Remaining entries: ${ENTRY_COUNT} (should be 1 - rootDN only)"
echo ""

# Summary
echo -e "${YELLOW}[4/4] Environment Reset Summary${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${GREEN}✓ PostgreSQL:${NC} All tables truncated"
echo -e "${GREEN}✓ OpenLDAP:${NC} All entries deleted (except base DN)"
echo -e "${GREEN}✓ Application:${NC} Stopped"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${GREEN}Environment is ready for fresh testing!${NC}"
echo ""
echo "Next steps:"
echo "  1. Start application: ./mvnw spring-boot:run"
echo "  2. Upload test file: curl -X POST http://localhost:8081/file/upload ..."
echo ""
