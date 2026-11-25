package com.jobportal.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobportal.entity.Job;
import com.jobportal.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.*;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

/** Springdoc remains authoritative; metadata supplements reflection where maps or business rules need explanation. */
@Component
public class ApiContractCustomizer implements GlobalOpenApiCustomizer {
    private final JsonNode metadata;

    public ApiContractCustomizer() throws IOException {
        try (var input = new ClassPathResource("openapi/operations.json").getInputStream()) {
            metadata = Json.mapper().readTree(input);
        }
    }

    @Override
    public void customise(OpenAPI api) {
        api.setServers(List.of(new io.swagger.v3.oas.models.servers.Server().url("/")));
        for (Class<?> model : List.of(ErrorResponse.class, Job.class)) {
            ModelConverters.getInstance().readAll(model).forEach(api.getComponents()::addSchemas);
        }
        api.getComponents().addHeaders("RateLimit-Limit", new Header().description("Configured requests per 60-second window per client IP; default 60").schema(new IntegerSchema()).example(60));
        api.getComponents().addHeaders("RateLimit-Remaining", new Header().description("Remaining requests in the current window").schema(new IntegerSchema()).example(59));
        api.getComponents().addHeaders("RateLimit-Reset", new Header().description("UTC Unix epoch seconds when this window expires").schema(new IntegerSchema().format("int64")).example(1795600860L));
        api.getComponents().addHeaders("Retry-After", new Header().description("Seconds until the current window expires").schema(new IntegerSchema()).example(60));
        metadata.path("schemas").fields().forEachRemaining(e -> api.getComponents().addSchemas(e.getKey(), schema(e.getValue())));
        metadata.path("modelFields").fields().forEachRemaining(e -> {
            Schema<?> model = api.getComponents().getSchemas().get(e.getKey());
            if (model != null && model.getProperties() != null) {
                model.setRequired(new ArrayList<>(model.getProperties().keySet()));
                e.getValue().fields().forEachRemaining(field -> {
                    Schema<?> property = model.getProperties().get(field.getKey());
                    if (property != null) {
                        property.setDescription(field.getValue().path("description").asText());
                        if (field.getValue().has("nullable")) property.setNullable(field.getValue().path("nullable").asBoolean());
                        if (field.getValue().has("format")) property.setFormat(field.getValue().path("format").isNull() ? null : field.getValue().path("format").asText());
                    }
                });
            }
        });
        api.getComponents().getSchemas().get("ErrorResponse").setRequired(List.of("timestamp", "status", "code", "error", "message", "path", "details"));
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
            String key = method.name() + " " + path;
            JsonNode docs = metadata.path("operations").path(key);
            if (docs.isMissingNode()) {
                if (path.startsWith("/api/v1/") || path.equals("/health")) throw new IllegalStateException("Missing API documentation: " + key);
                return;
            }
            op.setSummary(docs.path("summary").asText());
            op.setDescription(docs.path("description").asText());
            op.setSecurity(docs.path("public").asBoolean() ? List.of() : List.of(new SecurityRequirement().addList("Bearer Authentication")));
            op.setDeprecated(docs.path("deprecated").asBoolean());
            if (op.getParameters() != null) op.getParameters().forEach(parameter -> {
                JsonNode info = metadata.path("parameters").path(parameter.getName());
                if (!info.isMissingNode()) {
                    parameter.setDescription(info.path("description").asText());
                    parameter.setExample(value(info.path("example")));
                    if (info.has("schema")) parameter.setSchema(schema(info.path("schema")));
                }
            });
            if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                op.getRequestBody().setDescription("Required input fields are marked in the schema. Unknown JSON fields are ignored; do not supply server-generated IDs or timestamps.");
                op.getRequestBody().getContent().values().forEach(media -> {
                    if (docs.has("request")) media.setExample(value(docs.path("request")));
                });
            }
            if (path.equals("/api/v1/resumes/upload")) {
                op.setParameters(null);
                op.getRequestBody().setRequired(true);
                op.getRequestBody().setDescription("Multipart form fields: candidateId (positive profile ID) and file (nonempty PDF/DOC/DOCX, at most 5 MiB). Request total at most 6 MiB.");
                op.getRequestBody().getContent().get("multipart/form-data").setSchema(new ObjectSchema()
                        .addProperty("candidateId", new IntegerSchema().minimum(java.math.BigDecimal.ONE).example(1))
                        .addProperty("file", new StringSchema().format("binary"))
                        .required(List.of("candidateId", "file")));
            }
            ApiResponses responses = new ApiResponses();
            ApiResponse success = new ApiResponse().description(docs.path("successDescription").asText("Request completed successfully"));
            if (docs.has("schema")) {
                MediaType media = new MediaType().schema(schema(docs.path("schema")));
                if (docs.has("example")) media.addExamples("success", new Example().value(value(docs.path("example"))));
                if (docs.has("empty")) media.addExamples("empty", new Example().value(value(docs.path("empty"))));
                docs.path("additionalExamples").fields().forEachRemaining(example -> media.addExamples(example.getKey(), new Example().value(value(example.getValue()))));
                success.content(new Content().addMediaType(docs.path("mediaType").asText("application/json"), media));
            }
            if (docs.path("mediaType").asText().equals("application/octet-stream")) {
                success.addHeaderObject("Content-Disposition", new Header().description("Attachment filename generated by the server")
                        .schema(new StringSchema()).example("attachment; filename=\"resume_1_example.pdf\""));
            }
            responses.addApiResponse(docs.path("status").asText(), success);
            docs.path("errors").fields().forEachRemaining(entry -> {
                ApiResponse error = new ApiResponse().description(entry.getValue().path("trigger").asText());
                MediaType media = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
                entry.getValue().path("examples").fields().forEachRemaining(example -> {
                    Map<String,Object> body = new LinkedHashMap<>();
                    body.put("timestamp", "2026-11-25T10:00:00Z"); body.put("status", Integer.parseInt(entry.getKey()));
                    body.put("code", example.getKey()); body.put("error", org.springframework.http.HttpStatus.valueOf(Integer.parseInt(entry.getKey())).getReasonPhrase());
                    body.put("message", example.getValue().asText()); body.put("path", path.replaceAll("\\{[^}]+}", "1")); body.put("details", List.of());
                    media.addExamples(example.getKey(), new Example().value(body));
                });
                error.content(new Content().addMediaType("application/json", media));
                if (entry.getKey().equals("429")) error.addHeaderObject("Retry-After", new Header().$ref("#/components/headers/Retry-After"));
                responses.addApiResponse(entry.getKey(), error);
            });
            responses.values().forEach(response -> {
                for (String header : List.of("RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset"))
                    response.addHeaderObject(header, new Header().$ref("#/components/headers/" + header));
            });
            op.setResponses(responses);
        }));
    }

    private static Object value(JsonNode node) { return Json.mapper().convertValue(node, Object.class); }
    private static Schema<?> schema(JsonNode node) { return Json.mapper().convertValue(node, Schema.class); }
}
