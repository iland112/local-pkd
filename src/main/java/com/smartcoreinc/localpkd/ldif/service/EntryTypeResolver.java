package com.smartcoreinc.localpkd.ldif.service;

import java.util.Arrays;
import java.util.Collection;

import com.smartcoreinc.localpkd.enums.EntryType;
import com.unboundid.ldap.sdk.Attribute;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;

public class EntryTypeResolver {
    /**
     * Entry 객체의 objectClass에 따라 EntryType을 반환합니다.
     * objectClass가 다중 값 속성일 수 있으므로 모든 값을 검사합니다.
     *
     * @param entry LDAP Entry 객체
     * @return objectClass에 해당하는 EntryType, 해당하는 타입이 없으면 null
     * @throws LDAPException objectClass를 찾을 수 없을 경우
     */
    public static EntryType resolveEntryType(Entry entry) throws LDAPException {
        Attribute objectClassAttribute = entry.getAttribute("objectClass");
        if (objectClassAttribute == null) {
            throw new LDAPException(ResultCode.NO_SUCH_ATTRIBUTE, "objectClass attribute not found in the entry.");
        }

        Collection<String> objectClasses = Arrays.asList(objectClassAttribute.getValues());

        // 미리 정의된 우선순위에 따라 EntryType을 결정합니다.
        // 예를 들어, cRLDistributionPoint가 가장 중요하다고 가정합니다.
        if (objectClasses.contains("cRLDistributionPoint") && objectClasses.contains("pkdDownload")) {
            return EntryType.CRL;
        }
        if (objectClasses.contains("inetOrgPerson") && objectClasses.contains("pkdDownload")) {
            return EntryType.DSC;
        }
        if (objectClasses.contains("organization")) {
            return EntryType.O;
        }
        if (objectClasses.contains("country")) {
            return EntryType.C;
        }
        if (objectClasses.contains("domain")) {
            return EntryType.DC;
        }
        if (objectClasses.contains("pkdMasterList") && objectClasses.contains("pkdDownload")) {
            return EntryType.ML;
        }
        
        // 해당되는 타입이 없을 경우
        return EntryType.UNKNOWN;
    }
}
