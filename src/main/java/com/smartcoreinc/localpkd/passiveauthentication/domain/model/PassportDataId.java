package com.smartcoreinc.localpkd.passiveauthentication.domain.model;

import io.github.wimdeblauwe.jpearl.AbstractEntityId;

import java.util.UUID;

/**
 * PassportData ID - Type-safe entity identifier for PassportData Aggregate.
 *
 * <p>JPearl-based type-safe entity identifier using UUID internally.
 * Used as JPA {@code @EmbeddedId}.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Type-safe: Prevents confusion with other entity IDs</li>
 *   <li>Immutable: Cannot be changed after creation</li>
 *   <li>UUID-based: Guarantees uniqueness in distributed environments</li>
 *   <li>JPA support: Can be used as @EmbeddedId</li>
 * </ul>
 */
public class PassportDataId extends AbstractEntityId<UUID> {

    /**
     * JPA-only default constructor (protected).
     *
     * <p>Only used when JPA instantiates the entity.
     * Do not call directly.</p>
     */
    protected PassportDataId() {
        // JPA only
    }

    /**
     * UUID-based PassportData ID constructor.
     *
     * @param id UUID value
     */
    public PassportDataId(UUID id) {
        super(id);
    }

    /**
     * Create new PassportData ID (random UUID-based).
     *
     * <p>Use when creating a new passport verification.</p>
     *
     * @return new PassportData ID
     */
    public static PassportDataId newId() {
        return new PassportDataId(UUID.randomUUID());
    }

    /**
     * Create PassportData ID from string.
     *
     * <p>Use when converting UUID string from API request or external system
     * to PassportData ID.</p>
     *
     * @param id UUID string
     * @return PassportData ID
     * @throws IllegalArgumentException if UUID format is invalid
     */
    public static PassportDataId of(String id) {
        return new PassportDataId(UUID.fromString(id));
    }

    /**
     * Return internal UUID value.
     *
     * @return UUID value
     */
    public UUID toUUID() {
        return super.getId();
    }
}
