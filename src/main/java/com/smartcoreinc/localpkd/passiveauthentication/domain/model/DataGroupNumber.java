package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

/**
 * Data Group Number (DG1 ~ DG16) as per ICAO 9303 specification.
 *
 * <p>LDS (Logical Data Structure) defines 16 data groups:
 * <ul>
 *   <li>DG1: MRZ (Machine Readable Zone)</li>
 *   <li>DG2: Encoded Face</li>
 *   <li>DG3: Encoded Fingerprints</li>
 *   <li>DG4: Encoded Iris</li>
 *   <li>DG5-DG16: Additional biometric and other data</li>
 *   <li>DG15: Active Authentication Public Key</li>
 * </ul>
 */
public enum DataGroupNumber {
    DG1(1),
    DG2(2),
    DG3(3),
    DG4(4),
    DG5(5),
    DG6(6),
    DG7(7),
    DG8(8),
    DG9(9),
    DG10(10),
    DG11(11),
    DG12(12),
    DG13(13),
    DG14(14),
    DG15(15),
    DG16(16);

    private final int value;

    DataGroupNumber(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Get DataGroupNumber from integer value.
     *
     * @param value integer value (1-16)
     * @return corresponding DataGroupNumber
     * @throws IllegalArgumentException if value is out of range
     */
    public static DataGroupNumber fromInt(int value) {
        for (DataGroupNumber dgn : values()) {
            if (dgn.value == value) {
                return dgn;
            }
        }
        throw new IllegalArgumentException("Invalid Data Group Number: " + value + ". Must be between 1 and 16.");
    }

    /**
     * Get DataGroupNumber from string representation (e.g., "DG1", "DG15").
     *
     * @param str string representation
     * @return corresponding DataGroupNumber
     * @throws IllegalArgumentException if string format is invalid
     */
    public static DataGroupNumber fromString(String str) {
        if (str == null || !str.matches("^DG(1[0-6]|[1-9])$")) {
            throw new IllegalArgumentException("Invalid Data Group format: " + str + ". Expected format: DG1~DG16");
        }
        int number = Integer.parseInt(str.substring(2));
        return fromInt(number);
    }
}
