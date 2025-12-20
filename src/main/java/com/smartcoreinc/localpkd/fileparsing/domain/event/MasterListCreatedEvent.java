package com.smartcoreinc.localpkd.fileparsing.domain.event;

import com.smartcoreinc.localpkd.certificatevalidation.domain.model.CountryCode;
import com.smartcoreinc.localpkd.fileupload.domain.model.UploadId;
import com.smartcoreinc.localpkd.fileparsing.domain.model.MasterListId;
import com.smartcoreinc.localpkd.shared.domain.DomainEvent;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * MasterListCreatedEvent - Domain event fired when a Master List is successfully created
 *
 * <p>This event indicates that a Master List CMS binary has been parsed and
 * stored in the system. Subscribers can react to this event to perform:
 * <ul>
 *   <li>Upload Master List binary to LDAP</li>
 *   <li>Extract and store individual CSCA certificates</li>
 *   <li>Generate statistics and reports</li>
 * </ul>
 * </p>
 *
 * @author SmartCore Inc.
 * @version 1.0
 * @since 2025-11-27
 */
@Getter
@ToString
public class MasterListCreatedEvent implements DomainEvent {

    /**
     * 이벤트 고유 식별자
     */
    private final UUID eventId;

    /**
     * Master List ID
     */
    private final MasterListId masterListId;

    /**
     * 원본 파일 업로드 ID
     */
    private final UploadId uploadId;

    /**
     * 국가 코드
     */
    private final CountryCode countryCode;

    /**
     * CSCA 인증서 개수
     */
    private final int cscaCount;

    /**
     * 이벤트 발생 시각
     */
    private final LocalDateTime occurredOn;

    /**
     * MasterListCreatedEvent 생성자
     *
     * @param masterListId Master List ID
     * @param uploadId 원본 파일 업로드 ID
     * @param countryCode 국가 코드
     * @param cscaCount CSCA 인증서 개수
     */
    public MasterListCreatedEvent(MasterListId masterListId,
                                   UploadId uploadId,
                                   CountryCode countryCode,
                                   int cscaCount) {
        if (masterListId == null) {
            throw new IllegalArgumentException("MasterListId cannot be null");
        }
        if (uploadId == null) {
            throw new IllegalArgumentException("UploadId cannot be null");
        }
        if (countryCode == null) {
            throw new IllegalArgumentException("CountryCode cannot be null");
        }

        this.eventId = UUID.randomUUID();
        this.masterListId = masterListId;
        this.uploadId = uploadId;
        this.countryCode = countryCode;
        this.cscaCount = cscaCount;
        this.occurredOn = LocalDateTime.now();
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public LocalDateTime occurredOn() {
        return occurredOn;
    }

    @Override
    public String eventType() {
        return "MasterListCreated";
    }

    /**
     * Get the country code as string value
     */
    public String getCountryCodeValue() {
        return countryCode.getValue();
    }
}
