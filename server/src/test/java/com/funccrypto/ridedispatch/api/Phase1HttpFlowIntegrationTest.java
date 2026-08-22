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
import com.funccrypto.ridedispatch.order.OrderProgressEventRepository;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.order.TripStage;
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

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired AdminUserRepository adminRepository;
    @Autowired AuthSessionRepository sessionRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired DriverLocationCurrentRepository locationRepository;
    @Autowired RideOrderRepository orderRepository;
    @Autowired DispatchAttemptRepository attemptRepository;
    @Autowired OrderProgressEventRepository progressRepository;
    @Autowired OperationLogRepository operationLogRepository;
    @Autowired PlatformBrandRepository brandRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Clock clock;

    @BeforeEach
    void beforeEach() { cleanDatabase(); }

    @AfterEach
    void afterEach() { cleanDatabase(); }

    @Test
    void authenticatedHttpFlowCoversCreationDispatchFulfillmentAndQuery() throws Exception {
        adminRepository.save(new AdminUserEntity(
                "admin", passwordEncoder.encode("admin-password"), "系统管理员", AdminRole.ADMIN, clock.instant()));
        String adminToken = login("/api/v1/auth/admin/login", "admin", "admin-password");

        String departureAt = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1).withNano(0).toString();
        JsonNode adminCreated = json(postJson(
                "/api/v1/admin/orders",
                adminToken,
                orderBodyWithoutSource(departureAt, "13800000009")));
        assertThat(adminCreated.get("status").asText()).isEqualTo("PENDING_DISPATCH");
        assertThat(orderRepository.findByOrderNo(adminCreated.get("orderNo").asText()).orElseThrow().getSourceType().name())
                .isEqualTo("ADMIN_CREATED");

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
                """.formatted(OffsetDateTime.now(ZoneOffset.UTC).withNano(0)));

        JsonNode createdOrder = json(mockMvc.perform(post("/api/v1/public/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBodyWithSource(departureAt, "13800000000")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
        String orderNo = createdOrder.get("orderNo").asText();
        String passengerToken = createdOrder.get("passengerAccessToken").asText();

        JsonNode nearby = getJson("/api/v1/admin/orders/" + orderNo + "/nearby-drivers", adminToken);
        assertThat(nearby.size()).isEqualTo(1);
        assertThat(nearby.get(0).get("driverId").asLong()).isEqualTo(driverId);

        JsonNode dispatched = json(postJson(
                "/api/v1/admin/orders/" + orderNo + "/dispatch",
                adminToken,
                "{\"driverId\":" + driverId + "}"));
        long attemptId = dispatched.get("attemptId").asLong();

        JsonNode pending = getJson("/api/v1/driver/orders/pending-confirmation", driverToken);
        assertThat(pending.size()).isEqualTo(1);
        assertThat(pending.get(0).get("attemptId").asLong()).isEqualTo(attemptId);

        JsonNode accepted = json(mockMvc.perform(post("/api/v1/driver/dispatch-attempts/{attemptId}/accept", attemptId)
                        .header("Authorization", bearer(driverToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");

        JsonNode active = getJson("/api/v1/driver/orders/active", driverToken);
        assertThat(active.size()).isEqualTo(1);
        assertThat(active.get(0).get("orderNo").asText()).isEqualTo(orderNo);

        advance(orderNo, driverToken, TripStage.ARRIVED_PICKUP);
        advance(orderNo, driverToken, TripStage.PASSENGER_ONBOARD);
        advance(orderNo, driverToken, TripStage.IN_TRANSIT);
        JsonNode arrived = advance(orderNo, driverToken, TripStage.ARRIVED_DESTINATION);
        assertThat(arrived.get("status").asText()).isEqualTo("IN_SERVICE");
        assertThat(progressRepository.findByOrderIdOrderByOccurredAtAsc(
                orderRepository.findByOrderNo(orderNo).orElseThrow().getId())).hasSize(4);

        JsonNode pendingPayment = json(postJson(
                "/api/v1/driver/orders/" + orderNo + "/final-amount",
                driverToken,
                "{\"amount\":12800}"));
        assertThat(pendingPayment.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(pendingPayment.get("finalAmount").asLong()).isEqualTo(12800L);

        JsonNode passengerView = json(mockMvc.perform(get("/api/v1/public/orders/{orderNo}", orderNo)
                        .header("X-Passenger-Token", passengerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(passengerView.get("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(passengerView.get("tripStage").asText()).isEqualTo("ARRIVED_DESTINATION");
        assertThat(passengerView.get("finalAmount").asLong()).isEqualTo(12800L);

        JsonNode adminList = getJson("/api/v1/admin/orders?status=PENDING_PAYMENT", adminToken);
        assertThat(adminList.get("totalElements").asLong()).isEqualTo(1L);
        JsonNode detail = getJson("/api/v1/admin/orders/" + orderNo, adminToken);
        assertThat(detail.get("dispatchAttempts").size()).isEqualTo(1);
        assertThat(detail.get("progressEvents").size()).isEqualTo(4);
        assertThat(detail.get("operationLogs").size()).isGreaterThanOrEqualTo(7);
        assertThat(detail.get("operationLogs").get(0).get("action").asText()).isEqualTo("ORDER_DISPATCHED");

        var order = orderRepository.findByOrderNo(orderNo).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.getCurrentDriverId()).isEqualTo(driverId);
        assertThat(order.getFinalAmount()).isEqualTo(12800L);
    }

    @Test
    void adminBrandApiCoversReadUpdateAndDispatcherPermission() throws Exception {
        AdminUserEntity admin = adminRepository.save(new AdminUserEntity(
                "brand-admin", passwordEncoder.encode("admin-password"), "品牌管理员", AdminRole.ADMIN, clock.instant()));
        String adminToken = login("/api/v1/auth/admin/login", "brand-admin", "admin-password");
        String dispatcherToken = createDispatcherToken();

        JsonNode updated = json(putJson("/api/v1/admin/brand", adminToken, """
                {
                  "companyName":"真实车队",
                  "logoUrl":"https://cdn.example.com/logo.png"
                }
                """));
        assertThat(updated.get("companyName").asText()).isEqualTo("真实车队");
        assertThat(updated.get("logoUrl").asText()).isEqualTo("https://cdn.example.com/logo.png");

        JsonNode publicBrand = json(mockMvc.perform(get("/api/v1/public/brand"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(publicBrand.get("companyName").asText()).isEqualTo("真实车队");

        JsonNode dispatcherView = getJson("/api/v1/admin/brand", dispatcherToken);
        assertThat(dispatcherView.get("updatedAt").asText()).startsWith(updated.get("updatedAt").asText().substring(0, 19));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/admin/brand")
                        .header("Authorization", bearer(dispatcherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"未授权修改\"}"))
                .andExpect(status().isForbidden());
    }
    private JsonNode advance(String orderNo, String token, TripStage stage) throws Exception {
        return json(postJson(
                "/api/v1/driver/orders/" + orderNo + "/progress",
                token,
                "{\"stage\":\"" + stage.name() + "\"}"));
    }

    private String login(String path, String username, String password) throws Exception {
        JsonNode body = json(mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private JsonNode getJson(String path, String token) throws Exception {
        return json(mockMvc.perform(get(path).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String postJson(String path, String accessToken, String content) throws Exception {
        return mockMvc.perform(post(path)
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private String orderBodyWithSource(String departureAt, String mobile) {
        return """
                {
                  "sourceType":"PUBLIC_H5",
                  "pickup":{"address":"扬州东站","latitude":32.3910000,"longitude":119.5080000},
                  "destination":{"address":"瘦西湖","latitude":32.4200000,"longitude":119.4140000},
                  "passengerCount":2,
                  "departureAt":"%s",
                  "mobile":"%s"
                }
                """.formatted(departureAt, mobile);
    }

    private String orderBodyWithoutSource(String departureAt, String mobile) {
        return """
                {
                  "pickup":{"address":"扬州东站","latitude":32.3910000,"longitude":119.5080000},
                  "destination":{"address":"瘦西湖","latitude":32.4200000,"longitude":119.4140000},
                  "passengerCount":2,
                  "departureAt":"%s",
                  "mobile":"%s"
                }
                """.formatted(departureAt, mobile);
    }

    private String putJson(String path, String accessToken, String content) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(path)
                        .header("Authorization", bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private String createDispatcherToken() throws Exception {
        adminRepository.save(new AdminUserEntity(
                "brand-dispatcher", passwordEncoder.encode("dispatcher-password"), "调度员", AdminRole.DISPATCHER, clock.instant()));
        return login("/api/v1/auth/admin/login", "brand-dispatcher", "dispatcher-password");
    }
    private JsonNode json(String content) throws Exception { return jsonMapper.readTree(content); }
    private String bearer(String token) { return "Bearer " + token; }

    private void cleanDatabase() {
        sessionRepository.deleteAll();
        operationLogRepository.deleteAll();
        progressRepository.deleteAll();
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        locationRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverRepository.deleteAll();
        adminRepository.deleteAll();
        brandRepository.deleteAll();
    }
}
