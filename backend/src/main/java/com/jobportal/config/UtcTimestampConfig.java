package com.jobportal.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobportal.time.UtcTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.time.LocalDateTime;

@Configuration
public class UtcTimestampConfig {
    @Bean
    public JavaTimeModule utcTimestampModule() {
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
            public void serialize(LocalDateTime value, JsonGenerator output, SerializerProvider provider) throws IOException {
                output.writeString(UtcTime.format(value));
            }
        });
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
            public LocalDateTime deserialize(JsonParser input, DeserializationContext context) throws IOException {
                return UtcTime.parse(input.getText());
            }
        });
        return module;
    }
}
