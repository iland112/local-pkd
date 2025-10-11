package com.smartcoreinc.localpkd.enums;

public enum TaskType {
    UNKNOWN("Unknown Task Type"),
    PARSE("LDIF Parsing Task"),
    BIND("Binding Entry to LDAP");

    private final String description;

    TaskType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
