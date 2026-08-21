package com.funccrypto.ridedispatch.driver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicDriverApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired DriverRepository driverRepository;
    @Autowired VehicleRepository vehicleRepository;
    @Autowired Clock clock;

    @AfterEach
    void clean() {
        vehicleRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void publicQrProfileOnlyReturnsPassengerSafeFields() throws Exception {
        var now = clock.instant();
        DriverEntity driver = driverRepository.save(DriverEntity.create(
                "DPUBLIC01", "张师傅", "13800009999", 4, 4, "PUBLICQR01", now));
        VehicleEntity vehicle = vehicleRepository.save(new VehicleEntity(
                driver.getId(), "苏K12345", "测试车型", 4, now));
        driver.assignDefaultVehicle(vehicle.getId(), now);
        driverRepository.save(driver);

        mockMvc.perform(get("/api/v1/public/drivers/{shortCode}", "PUBLICQR01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("张师傅"))
                .andExpect(jsonPath("$.plateNo").value("苏K12345"))
                .andExpect(jsonPath("$.brandModel").value("测试车型"))
                .andExpect(jsonPath("$.maxPassengers").value(4))
                .andExpect(jsonPath("$.mobile").doesNotExist())
                .andExpect(jsonPath("$.driverNo").doesNotExist());
    }
}
