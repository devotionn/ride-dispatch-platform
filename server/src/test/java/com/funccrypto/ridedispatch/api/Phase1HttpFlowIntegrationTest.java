package com.funccrypto.ridedispatch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.auth.AdminRole;
import com.funccrypto.ridedispatch.auth.AdminUserEntity;
import com.funccrypto.ridedispatch.auth.AdminUserRepository;
import com.funccrypto.ridedispatch.auth.AuthSessionRepository;
import com.funccrypto.ridedispatch.brand.PlatformBrandRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentRepository;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.driver.VehicleRepository;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Phase1HttpFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    AdminUserRepository adminRepository;

    @Autowired
    AuthSessionRepository sessionRepository;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    VehicleRepository vehicleRepository;

    @Autowired
    DriverLocationCurrentRepository locationRepository;

    @Autowired
    RideOrderRepository orderRepository;

    @Autowired
    DispatchAttemptRepository attemptRepository;

    @Autowired
    OperationLogRepository operationLogRepository;

    @Autowired
    PlatformBrandRepository brandRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    @BeforeEach
    void beforeEach() {
        cleanDatabase();
    }

    @AfterEach
    void afterEach() {
        cleanDatabase();
    }

    @Test
    void authenticatedHttpFlowCreatesDispatchesAndAcceptsOrder() throws Exception {
        adminRepository.save(new AdminUserEntity(
                "admin",
                passwordEncoder.encode("admin-password"),
                "系统管理员",
                AdminRole.ADMIN,
                clock.instant()));

        String adminToken = login("/api/v1/auth/admin/login", "admin", "admin-password");

        JsonNode createdDriver = json(postJson(
                "/api/v1/admin/drivers",
                adminToken,
                """
                {
                  "driverNo":"DHTTP01",
                  "name":"HTTP测试司机",
                  "mobile":"13800001001",
                  "password":"driver-password",
                  "maxPassengers":4,
                  "availablePassengers":4,
                  "plateNo":"苏KHTTP01",
                  "brandModel":"测试车型"
                }
                """));
        long driverId = createdDriver.get("id").asLong();

        String driverToken = login("/api/v1/auth/driver/login", "DHTTP01", "driver-password");
        String locatedAt = OffsetDateTime.now(ZoneOffset.UTC).withNano(0).toString();
        postJson(
                "/api/v1/driver/me/location",
                driverToken,
                """
                {
                  "latitude":32.3910000,
                  "longitude":119.5080000,
                  "accuracyMeters":10,
                  "locatedAt":"%s",
                  "source":"DRIVER_APP"
                }
                """.formatted(locatedAt));

        String departureAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).withNano(0).toString();
        JsonNode createdOrder = json(mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceType":"PUBLIC_H5",
                                  "pickup":{"address":"扬州东站","latitude":32.3910000,"longitude":119.5080000},
                                  "destination":{"address":"瘦西湖","latitude":32.4200000,"longitude":119.4140000},
                                  "passengerCount":2,
                                  "departureAt":"%s",
                                  "mobile":"13800000000"
                                }
                                """.formatted(departureAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String orderNo = createdOrder.get("orderNo").asText();

        JsonNode nearby = json(mockMvc.perform(get("/api/v1/admin/orders/{orderNo}/nearby-drivers", orderNo)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(nearby.isArray()).isTrue();
        assertThat(nearby.size()).isEqualTo(1);
        assertThat(nearby.get(0).get("driverId").asLong()).isEqualTo(driverId);

        JsonNode dispatched = json(postJson(
                "/api/v1/admin/orders/" + orderNo + "/dispatch",
                adminToken,
                "{\"driverId\":" + driverId + "}"));
        long attemptId = dispatched.get("attemptId").asLong();

        JsonNode accepted = json(mockMvc.perform(post(
                        "/api/v1/driver/dispatch-attempts/{attemptId}/accept", attemptId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        var order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getCurrentDriverId()).isEqualTo(driverId);
    }

    private String login(String path, String username, String password) throws Exception {
        JsonNode body = json(mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String postJson(String path, String accessToken, String content) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode json(String content) throws Exception {
        return jsonMapper.readTree(content);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private void cleanDatabase() {
        sessionRepository.deleteAll();
        operationLogRepository.deleteAll();
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        locationRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverRepository.deleteAll();
        adminRepository.deleteAll();
        brandRepository.deleteAll();
    }
}
