package com.smartcoreinc.localpkd.icao.service;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapNameBuilder;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LdapEntryService {

    private final LdapTemplate ldapTemplate;

    public LdapEntryService(LdapTemplate ldapTemplate) {
        this.ldapTemplate = ldapTemplate;
    }

    
}
