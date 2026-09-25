package com.foodflow.config;

import com.foodflow.exception.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * OpenAPI (Swagger) setup. springdoc generates the spec from the controllers and DTOs at runtime
 * (the paths, parameters and request/response schemas, including their Bean Validation
 * constraints), so the documentation can't drift away from the code. This class adds what code
 * alone can't express:
 * <ul>
 *   <li>API description and grouping (tags),</li>
 *   <li>the JWT "bearer" scheme, which enables Swagger UI's "Authorize" button,</li>
 *   <li>the shared ErrorResponse format on every operation.</li>
 * </ul>
 * UI: /swagger-ui.html · Raw spec: /v3/api-docs (can be imported into Postman or used to generate clients).
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    @Bean
    public OpenAPI foodFlowOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Log in with POST /api/auth/login, then paste the accessToken here (without 'Bearer')."));
        // Register ErrorResponse so every operation can reference it.
        Map<String, Schema> errorSchemas = ModelConverters.getInstance().read(ErrorResponse.class);
        errorSchemas.forEach(components::addSchemas);

        return new OpenAPI()
                .info(new Info()
                        .title("FoodFlow API")
                        .version("1.0")
                        .description("""
                                REST API of FoodFlow, a food-ordering platform (customers, restaurant owners, admins).

                                **Authentication:** JWT bearer tokens. Get one from `/api/auth/login` or `/api/auth/register`, \
                                then click **Authorize**. Endpoints marked without a lock are public.

                                **Errors** always use one JSON shape (`ErrorResponse`): `status`, a stable machine-readable \
                                `error` code (e.g. `CART_RESTAURANT_MISMATCH`, `COUPON_EXPIRED`), a human `message`, \
                                and `fieldErrors` for validation failures.

                                **Money** is never accepted from clients: all prices, totals, discounts and payment amounts \
                                are computed on the server.""")
                        .contact(new Contact().name("FoodFlow").email("dev@foodflow.example")))
                // Secured by default; public endpoints opt out with @SecurityRequirements().
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .components(components)
                .tags(List.of(
                        new Tag().name("Auth").description("Register and log in (returns a JWT)"),
                        new Tag().name("Users").description("The current user's profile and dashboard"),
                        new Tag().name("Restaurants").description("Public browsing, search and owner management"),
                        new Tag().name("Menu").description("Menu items (dishes)"),
                        new Tag().name("Categories").description("Food categories"),
                        new Tag().name("Cart").description("The customer's cart (one restaurant at a time)"),
                        new Tag().name("Orders").description("Checkout, tracking, status changes, cancellation"),
                        new Tag().name("Payments").description("Simulated payment gateway"),
                        new Tag().name("Coupons").description("Discount coupons"),
                        new Tag().name("Reviews").description("Restaurant reviews (after a delivered order)"),
                        new Tag().name("Addresses").description("The customer's saved delivery addresses"),
                        new Tag().name("Owner").description("Restaurant-owner dashboard and order management"),
                        new Tag().name("Admin").description("Platform administration and analytics")));
    }

    /**
     * Documents the standard error responses on every operation, all with the ErrorResponse
     * body, unless the operation already declares that status itself.
     */
    @Bean
    public OperationCustomizer standardErrorResponses() {
        return (operation, handlerMethod) -> {
            ApiResponses responses = operation.getResponses();
            addIfAbsent(responses, "400", "Invalid request: validation failed (see fieldErrors) or malformed JSON");
            if (operation.getSecurity() == null || !operation.getSecurity().isEmpty()) {
                addIfAbsent(responses, "401", "Missing, invalid or expired token");
                addIfAbsent(responses, "403", "Authenticated, but not allowed (wrong role or not your resource)");
            }
            addIfAbsent(responses, "404", "Resource not found, or not visible to you");
            addIfAbsent(responses, "409", "Conflicts with current state (duplicate, business rule, concurrent change)");
            addIfAbsent(responses, "500", "Unexpected server error (details are logged, never returned)");
            return operation;
        };
    }

    private static void addIfAbsent(ApiResponses responses, String status, String description) {
        if (!responses.containsKey(status)) {
            responses.addApiResponse(status, new ApiResponse().description(description).content(new Content()
                    .addMediaType("application/json", new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
        }
    }
}
