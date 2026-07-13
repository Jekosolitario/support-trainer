package it.zuperman.support_trainer.common.time;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock applicationClock(TimeProperties timeProperties) {
        return Clock.system(timeProperties.clockZone());
    }
}
