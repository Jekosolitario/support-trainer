package it.zuperman.support_trainer.common.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;

@Configuration
public class JacksonConfiguration {

    @Bean
    JsonMapperBuilderCustomizer strictJsonMapperBuilderCustomizer() {
        return builder -> builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
    }
}
