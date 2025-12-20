#!/bin/bash

##############################################################################
# ICAO PKD Upload Data Verification Script
#
# Description:
#   Verifies uploaded certificate and CRL data by comparing PostgreSQL
#   database contents with LDAP server entries.
#
# Usage:
#   ./scripts/verify-upload-data.sh [OPTIONS]
#
# Options:
#   -h, --help       Show this help message
#   -v, --verbose    Enable verbose output
#   -j, --json       Output results in JSON format
#
# Environment Variables:
#   PGHOST          PostgreSQL host (default: localhost)
#   PGPORT          PostgreSQL port (default: 5432)
#   PGUSER          PostgreSQL user (default: postgres)
#   PGPASSWORD      PostgreSQL password (default: secret)
#   PGDATABASE      PostgreSQL database (default: icao_local_pkd)
#   LDAP_HOST       LDAP server host (default: 192.168.100.10)
#   LDAP_PORT       LDAP port (default: 389)
#   LDAP_BIND_DN    LDAP bind DN (default: cn=admin,dc=ldap,dc=smartcoreinc,dc=com)
#   LDAP_PASSWORD   LDAP password (default: core)
#   LDAP_BASE_DN    LDAP base DN (default: dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com)
#
# Exit Codes:
#   0 - Success (all verifications passed)
#   1 - Error (verification failed or command error)
#
# Author: Claude (Anthropic) & SmartCore Inc.
# Version: 1.0
# Date: 2025-12-11
##############################################################################

set -euo pipefail

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Default configuration
VERBOSE=false
JSON_OUTPUT=false

# PostgreSQL configuration
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-secret}"
PGDATABASE="${PGDATABASE:-icao_local_pkd}"

# LDAP configuration
LDAP_HOST="${LDAP_HOST:-192.168.100.10}"
LDAP_PORT="${LDAP_PORT:-389}"
LDAP_BIND_DN="${LDAP_BIND_DN:-cn=admin,dc=ldap,dc=smartcoreinc,dc=com}"
LDAP_PASSWORD="${LDAP_PASSWORD:-core}"
LDAP_BASE_DN="${LDAP_BASE_DN:-dc=data,dc=download,dc=pkd,dc=ldap,dc=smartcoreinc,dc=com}"

##############################################################################
# Helper Functions
##############################################################################

print_header() {
    if [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "${BLUE}========================================${NC}"
        echo -e "${BLUE}$1${NC}"
        echo -e "${BLUE}========================================${NC}"
    fi
}

print_section() {
    if [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "\n${CYAN}--- $1 ---${NC}"
    fi
}

print_success() {
    if [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "${GREEN}✓ $1${NC}"
    fi
}

print_warning() {
    if [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "${YELLOW}⚠ $1${NC}"
    fi
}

print_error() {
    if [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "${RED}✗ $1${NC}"
    fi
}

print_info() {
    if [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "  $1"
    fi
}

verbose() {
    if [[ "$VERBOSE" == true ]] && [[ "$JSON_OUTPUT" == false ]]; then
        echo -e "${YELLOW}[DEBUG]${NC} $1" >&2
    fi
}

show_help() {
    grep '^#' "$0" | sed 's/^#//' | grep -E '^#|^ ' | sed 's/^# //'
}

##############################################################################
# PostgreSQL Query Functions
##############################################################################

psql_query() {
    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -t -A -c "$1"
}

psql_count() {
    psql_query "$1" | head -1
}

##############################################################################
# LDAP Query Functions
##############################################################################

ldap_count() {
    ldapsearch -x -H "ldap://${LDAP_HOST}:${LDAP_PORT}" \
        -b "$LDAP_BASE_DN" \
        -D "$LDAP_BIND_DN" \
        -w "$LDAP_PASSWORD" \
        "$1" dn 2>/dev/null | grep -c "^dn:" || echo "0"
}

##############################################################################
# Verification Functions
##############################################################################

verify_database_certificates() {
    print_section "Database Certificates"

    local total_certs=$(psql_count "SELECT COUNT(*) FROM certificate;")
    local csca_count=$(psql_count "SELECT COUNT(*) FROM certificate WHERE certificate_type = 'CSCA';")
    local dsc_count=$(psql_count "SELECT COUNT(*) FROM certificate WHERE certificate_type = 'DSC';")
    local null_country=$(psql_count "SELECT COUNT(*) FROM certificate WHERE subject_country_code IS NULL;")

    print_info "Total Certificates: $total_certs"
    print_info "  - CSCA: $csca_count"
    print_info "  - DSC: $dsc_count"
    print_info "  - Null Country Code: $null_country"

    if [[ "$null_country" -eq 0 ]]; then
        print_success "Country code extraction: 100% success"
    else
        print_warning "Country code extraction: $null_country certificates with null country code"
    fi

    echo "$total_certs|$csca_count|$dsc_count|$null_country"
}

verify_database_crls() {
    print_section "Database CRLs"

    local total_crls=$(psql_count "SELECT COUNT(*) FROM certificate_revocation_list;")

    print_info "Total CRLs: $total_crls"

    echo "$total_crls"
}

verify_validation_status() {
    print_section "Validation Status"

    local valid_certs=$(psql_count "SELECT COUNT(*) FROM certificate WHERE validation_overall_status = 'VALID';")
    local invalid_certs=$(psql_count "SELECT COUNT(*) FROM certificate WHERE validation_overall_status = 'INVALID';")
    local expired_certs=$(psql_count "SELECT COUNT(*) FROM certificate WHERE validation_overall_status = 'EXPIRED';")
    local total_certs=$(psql_count "SELECT COUNT(*) FROM certificate;")

    local valid_pct=$(echo "scale=1; $valid_certs * 100 / $total_certs" | bc)
    local invalid_pct=$(echo "scale=1; $invalid_certs * 100 / $total_certs" | bc)
    local expired_pct=$(echo "scale=1; $expired_certs * 100 / $total_certs" | bc)

    print_info "Valid: $valid_certs ($valid_pct%)"
    print_info "Invalid: $invalid_certs ($invalid_pct%)"
    print_info "Expired: $expired_certs ($expired_pct%)"

    echo "$valid_certs|$invalid_certs|$expired_certs"
}

verify_ldap_certificates() {
    print_section "LDAP Certificates"

    local total_ldap=$(ldap_count "(objectClass=pkdDownload)")
    local csca_ldap=$(ldap_count "(&(o=csca)(objectClass=pkdDownload))")
    local dsc_ldap=$(ldap_count "(&(o=dsc)(objectClass=pkdDownload))")

    print_info "Total Certificates: $total_ldap"
    print_info "  - CSCA (approx): $csca_ldap"
    print_info "  - DSC (approx): $dsc_ldap"

    echo "$total_ldap|$csca_ldap|$dsc_ldap"
}

verify_ldap_crls() {
    print_section "LDAP CRLs"

    local total_crls_ldap=$(ldap_count "(objectClass=cRLDistributionPoint)")

    print_info "Total CRLs: $total_crls_ldap"

    echo "$total_crls_ldap"
}

verify_uploaded_flags() {
    print_section "Uploaded to LDAP Flags"

    local total_certs=$(psql_count "SELECT COUNT(*) FROM certificate;")
    local uploaded_true=$(psql_count "SELECT COUNT(*) FROM certificate WHERE uploaded_to_ldap = true;")
    local uploaded_false=$(psql_count "SELECT COUNT(*) FROM certificate WHERE uploaded_to_ldap = false;")

    local uploaded_pct=$(echo "scale=1; $uploaded_true * 100 / $total_certs" | bc)

    print_info "Uploaded (flag=true): $uploaded_true ($uploaded_pct%)"
    print_info "Not uploaded (flag=false): $uploaded_false"

    if [[ "$uploaded_true" -eq "$total_certs" ]]; then
        print_success "All certificates marked as uploaded"
    elif [[ "$uploaded_true" -gt 0 ]]; then
        print_warning "Partial upload flags: $uploaded_true/$total_certs"
    else
        print_warning "No certificates marked as uploaded (flag update bug)"
    fi

    echo "$uploaded_true|$uploaded_false"
}

compare_db_ldap() {
    print_section "Database vs LDAP Comparison"

    local db_certs=$(psql_count "SELECT COUNT(*) FROM certificate;")
    local ldap_certs=$(ldap_count "(objectClass=pkdDownload)")
    local diff_certs=$((db_certs - ldap_certs))

    local db_crls=$(psql_count "SELECT COUNT(*) FROM certificate_revocation_list;")
    local ldap_crls=$(ldap_count "(objectClass=cRLDistributionPoint)")
    local diff_crls=$((db_crls - ldap_crls))

    print_info "Certificates: DB=$db_certs, LDAP=$ldap_certs, Diff=$diff_certs"
    print_info "CRLs: DB=$db_crls, LDAP=$ldap_crls, Diff=$diff_crls"

    if [[ "$diff_certs" -eq 0 ]]; then
        print_success "Perfect match: All certificates uploaded"
    elif [[ "$diff_certs" -lt 50 ]]; then
        print_warning "Minor difference: $diff_certs certificates missing (${diff_certs}/${db_certs})"
    else
        print_error "Major difference: $diff_certs certificates missing"
    fi

    if [[ "$diff_crls" -eq 0 ]]; then
        print_success "Perfect match: All CRLs uploaded"
    else
        print_error "CRL mismatch: $diff_crls CRLs missing"
    fi

    echo "$diff_certs|$diff_crls"
}

verify_country_distribution() {
    print_section "Top 10 Country Distribution"

    verbose "Querying top 10 countries by certificate count..."

    psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -c \
        "SELECT subject_country_code as country, COUNT(*) as count
         FROM certificate
         WHERE subject_country_code IS NOT NULL
         GROUP BY subject_country_code
         ORDER BY count DESC
         LIMIT 10;" 2>/dev/null | grep -v "^$" || true
}

##############################################################################
# Main Verification Flow
##############################################################################

main() {
    # Parse command line arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -j|--json)
                JSON_OUTPUT=true
                shift
                ;;
            *)
                echo "Unknown option: $1"
                show_help
                exit 1
                ;;
        esac
    done

    if [[ "$JSON_OUTPUT" == false ]]; then
        print_header "ICAO PKD Data Verification"
        echo ""
        echo "Database: ${PGHOST}:${PGPORT}/${PGDATABASE}"
        echo "LDAP: ${LDAP_HOST}:${LDAP_PORT}"
        echo ""
    fi

    # Check dependencies
    verbose "Checking required commands..."
    for cmd in psql ldapsearch bc; do
        if ! command -v $cmd &> /dev/null; then
            print_error "Required command not found: $cmd"
            exit 1
        fi
        verbose "  ✓ $cmd found"
    done

    # Run verifications
    db_certs_result=$(verify_database_certificates)
    db_crls_result=$(verify_database_crls)
    validation_result=$(verify_validation_status)
    ldap_certs_result=$(verify_ldap_certificates)
    ldap_crls_result=$(verify_ldap_crls)
    uploaded_flags_result=$(verify_uploaded_flags)
    comparison_result=$(compare_db_ldap)

    # Display country distribution
    if [[ "$JSON_OUTPUT" == false ]]; then
        verify_country_distribution
    fi

    # JSON output
    if [[ "$JSON_OUTPUT" == true ]]; then
        IFS='|' read -r db_total db_csca db_dsc db_null_country <<< "$db_certs_result"
        IFS='|' read -r valid invalid expired <<< "$validation_result"
        IFS='|' read -r ldap_total ldap_csca ldap_dsc <<< "$ldap_certs_result"
        IFS='|' read -r uploaded_true uploaded_false <<< "$uploaded_flags_result"
        IFS='|' read -r diff_certs diff_crls <<< "$comparison_result"

        cat <<EOF
{
  "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "database": {
    "certificates": {
      "total": $db_total,
      "csca": $db_csca,
      "dsc": $db_dsc,
      "null_country_code": $db_null_country
    },
    "crls": $db_crls_result,
    "validation": {
      "valid": $valid,
      "invalid": $invalid,
      "expired": $expired
    },
    "uploaded_flags": {
      "true": $uploaded_true,
      "false": $uploaded_false
    }
  },
  "ldap": {
    "certificates": $ldap_total,
    "crls": $ldap_crls_result
  },
  "comparison": {
    "certificates_diff": $diff_certs,
    "crls_diff": $diff_crls
  },
  "success": $([ $diff_certs -lt 50 ] && [ $diff_crls -eq 0 ] && echo "true" || echo "false")
}
EOF
    else
        # Summary
        print_header "Verification Summary"

        IFS='|' read -r diff_certs diff_crls <<< "$comparison_result"

        if [[ "$diff_certs" -lt 50 ]] && [[ "$diff_crls" -eq 0 ]]; then
            print_success "Overall Status: PASS"
            exit 0
        else
            print_error "Overall Status: FAIL"
            print_info "  - Certificate difference: $diff_certs"
            print_info "  - CRL difference: $diff_crls"
            exit 1
        fi
    fi
}

# Run main function
main "$@"
