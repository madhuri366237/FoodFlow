package com.foodflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the FoodFlow backend.
 *
 * <p>{@code @SpringBootApplication} combines three annotations:
 * <ul>
 *   <li>{@code @Configuration} - this class can declare beans.</li>
 *   <li>{@code @EnableAutoConfiguration} - Spring Boot configures a DataSource, Hibernate,
 *       Jackson, Tomcat, etc. based on the libraries it finds on the classpath.</li>
 *   <li>{@code @ComponentScan} - every {@code @Component}, {@code @Service}, {@code @Repository}
 *       and {@code @RestController} under {@code com.foodflow} is registered in the IoC container.</li>
 * </ul>
 *
 * <p>{@code @ConfigurationPropertiesScan} registers the {@code @ConfigurationProperties}
 * records in the config package (JwtProperties, CorsProperties).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FoodFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FoodFlowApplication.class, args);
    }
}
