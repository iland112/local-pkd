package com.smartcoreinc.localpkd.ldaphelper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.ldap.support.LdapNameBuilder;
import javax.naming.Name;
import java.io.IOException;

public class NameDeserializer extends JsonDeserializer<Name> {
    @Override
    public Name deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getValueAsString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        return LdapNameBuilder.newInstance(value).build();
    }
}