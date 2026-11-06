package com.cineverse.shared;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.*;

import java.io.IOException;
import java.time.*;

@Configuration
public class UtcJsonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer utcDatabaseTimes() {
        return builder ->
                builder.serializerByType(
                        LocalDateTime.class,
                        new JsonSerializer<LocalDateTime>() {
                            public void serialize(
                                    LocalDateTime value,
                                    JsonGenerator gen,
                                    SerializerProvider provider)
                                    throws IOException {
                                gen.writeString(value.toInstant(ZoneOffset.UTC).toString());
                            }
                        });
    }
}
