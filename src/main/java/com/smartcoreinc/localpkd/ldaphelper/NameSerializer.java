package com.smartcoreinc.localpkd.ldaphelper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import javax.naming.Name;
import java.io.IOException;

public class NameSerializer extends JsonSerializer<Name> {
    @Override
    public void serialize(Name value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value != null) {
            gen.writeString(value.toString());
        } else {
            gen.writeNull();
        }
    }
}
