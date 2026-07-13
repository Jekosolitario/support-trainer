package it.zuperman.support_trainer.common.time;

import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("app.time")
public record TimeProperties(
        @DefaultValue("Europe/Rome") ZoneId businessZone,
        @DefaultValue("UTC") ZoneId clockZone
) {

    public TimeProperties {
        if (businessZone == null) {
            throw new IllegalArgumentException("app.time.business-zone must be a valid ZoneId");
        }
        if (clockZone == null || !ZoneOffset.UTC.equals(clockZone.normalized())) {
            throw new IllegalArgumentException("app.time.clock-zone must represent UTC");
        }
    }
}
