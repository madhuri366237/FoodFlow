package com.foodflow.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodflow.ApiIntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the generated OpenAPI spec, so an undocumented endpoint fails the build instead of
 * quietly appearing without a summary.
 */
class ApiDocumentationTest extends ApiIntegrationTestBase {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "patch", "delete");

    private JsonNode spec() throws Exception {
        return body(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()));
    }

    private List<JsonNode> operations(JsonNode spec) {
        List<JsonNode> operations = new ArrayList<>();
        spec.get("paths").forEach(path -> path.fields().forEachRemaining(entry -> {
            if (HTTP_METHODS.contains(entry.getKey())) operations.add(entry.getValue());
        }));
        return operations;
    }

    @Test
    void everyEndpointHasASummaryATagAndTheErrorFormat() throws Exception {
        List<JsonNode> operations = operations(spec());

        assertThat(operations).hasSizeGreaterThanOrEqualTo(54);
        assertThat(operations).allSatisfy(operation -> {
            assertThat(operation.path("summary").asText()).isNotBlank();
            assertThat(operation.path("tags")).isNotEmpty();
            assertThat(operation.path("responses").path("400").path("content").path("application/json")
                    .path("schema").path("$ref").asText()).endsWith("/ErrorResponse");
        });
    }

    @Test
    void jwtBearerSchemeIsDeclaredAndPublicEndpointsNeedNoToken() throws Exception {
        JsonNode spec = spec();

        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/components/securitySchemes/bearerAuth/bearerFormat").asText()).isEqualTo("JWT");
        // Explicit empty "security" = no lock icon in Swagger UI.
        assertThat(spec.at("/paths/~1api~1auth~1login/post/security")).isEmpty();
        assertThat(spec.at("/paths/~1api~1restaurants/get/security")).isEmpty();
        // Protected endpoints inherit the global bearer requirement and document 401.
        assertThat(spec.at("/paths/~1api~1orders/post/security").isMissingNode()).isTrue();
        assertThat(spec.at("/paths/~1api~1orders/post/responses/401").isMissingNode()).isFalse();
    }

    @Test
    void searchFiltersAreIndividualQueryParameters() throws Exception {
        List<String> names = new ArrayList<>();
        spec().at("/paths/~1api~1restaurants/get/parameters").forEach(p -> names.add(p.get("name").asText()));

        assertThat(names).contains("keyword", "categoryId", "minRating", "open", "minPrice", "maxPrice", "page", "size", "sort");
    }

    @Test
    void requestExamplesArePublished() throws Exception {
        JsonNode schemas = spec().path("components").path("schemas");

        assertThat(schemas.at("/PaymentRequest/properties/paymentToken/example").asText()).isEqualTo("tok_visa");
        assertThat(schemas.at("/CreateOrderRequest/required").toString()).contains("addressId", "paymentMethod");
    }

    @Test
    void swaggerUiIsServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
