package com.swiftdrop.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts real PostgreSQL and RabbitMQ containers once for all integration tests (Docker must be running).
 * Flyway creates the schema and demo users, exactly like production.
 * Email sending is replaced with a mock so no SMTP server is needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Password@123";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("swiftdrop.upload-dir", () -> "target/test-uploads");
        // Email is mocked in tests, so the SMTP health check has nothing to check
        registry.add("management.health.mail.enabled", () -> "false");
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @MockBean
    protected JavaMailSender mailSender;

    // ---------- helpers ----------

    protected String login(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("accessToken").asText();
    }

    /** Registers a brand-new customer and returns its access token. */
    protected String registerCustomer() throws Exception {
        String email = "test" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123\",\"fullName\":\"Test User\",\"phone\":\"0711111111\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("accessToken").asText();
    }

    /** Estimate + confirm, like the booking screen. Returns the created parcel JSON. */
    protected JsonNode createParcel(String customerToken) throws Exception {
        MvcResult estimate = mvc.perform(post("/api/v1/parcels/estimate")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destZoneId\":3,\"weightKg\":3.5,\"size\":\"MEDIUM\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String fee = read(estimate).get("fee").asText();

        MvcResult created = mvc.perform(post("/api/v1/parcels")
                        .header("Authorization", bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pickupAddress":"No. 12, Galle Road, Colombo 03",
                                 "dropoffAddress":"No. 45, Peradeniya Road, Kandy",
                                 "destZoneId":3,"recipientName":"Sunil Bandara","recipientPhone":"0771234567",
                                 "size":"MEDIUM","weightKg":3.5,"expectedFee":%s}
                                """.formatted(fee)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(created);
    }

    protected String courierId(String staffToken, String name) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/dispatch/couriers").header("Authorization", bearer(staffToken)))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode c : read(result)) {
            if (name.equals(c.get("name").asText())) {
                return c.get("courierId").asText();
            }
        }
        throw new IllegalStateException("Courier not found: " + name);
    }

    protected void assign(String staffToken, String parcelId, String courierId) throws Exception {
        mvc.perform(post("/api/v1/dispatch/parcels/{id}/assign", parcelId)
                        .header("Authorization", bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courierId\":\"" + courierId + "\"}"))
                .andExpect(status().isOk());
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
