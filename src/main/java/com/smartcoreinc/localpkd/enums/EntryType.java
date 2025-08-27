package com.smartcoreinc.localpkd.enums;

public enum EntryType {
    UNKNOWN("Unknown Entry Type"),
    ML("Master List"),
    DSC("Document Signer Certificate"),
    CRL("Certificate Revocation List"),
    O("Organization"),
    C("Country"),
    DC("Domain");


    private final String description;

    EntryType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
