package com.smartcoreinc.localpkd.enums;

public enum EntryType {
    ML("Master List"),
    DSC("Document Signer Certificate"),
    CRL("Certificate Revocation List");

    private final String description;

    EntryType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
