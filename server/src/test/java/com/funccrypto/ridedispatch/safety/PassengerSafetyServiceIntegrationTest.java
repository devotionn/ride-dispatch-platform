package com.funccrypto.ridedispatch.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PassengerSafetyServiceIntegrationTest {

    @Autowired
    PassengerSafetyService safetyService;

    @Autowired
    PublicOrderService publicOrderService;

    @Autowired
    SafetyAlarmRepository alarmRepository;

    @Autowired
    PassengerComplaintRepository complaintRepository;

    @Autowired
    OperationLogRepository operationLogRepository;

    @BeforeEach
    void beforeEach() {
        alarmRepository.deleteAll();
        complaintRepository.deleteAll();
        operationLogRepository.deleteAll();
    }

    @AfterEach
    void afterEach() {
        alarmRepository.deleteAll();
        complaintRepository.deleteAll();
        operationLogRepository.deleteAll();
    }

    @Test
    void alarmWithoutOrderContextIsAcceptedAnonymously() {
        SafetyAlarmEntity alarm = safetyService.reportAlarm(null, null, "RIDE_CREATE",
                new BigDecimal("32.391000"), new BigDecimal("119.412000"), "扬州东站南广场");

        assertThat(alarm.getId()).isNotNull();
        assertThat(alarm.getOrderNo()).isNull();
        assertThat(alarm.getPassengerMobile()).isNull();
        assertThat(alarm.getSourcePage()).isEqualTo("RIDE_CREATE");
        assertThat(alarmRepository.count()).isEqualTo(1);
    }

    @Test
    void alarmWithOrderTokenStoresOrderContext() {
        var result = publicOrderService.create(command());

        SafetyAlarmEntity alarm = safetyService.reportAlarm(result.orderNo(), result.passengerAccessToken(),
                "ORDER_STATUS", new BigDecimal("32.391000"), null, null);

        assertThat(alarm.getOrderNo()).isEqualTo(result.orderNo());
        assertThat(alarm.getPassengerMobile()).isEqualTo("13800000000");
    }

    @Test
    void alarmWithOrderButMissingTokenIsRejected() {
        var result = publicOrderService.create(command());

        assertThatThrownBy(() -> safetyService.reportAlarm(result.orderNo(), null, "ORDER_STATUS", null, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("PASSENGER_TOKEN_REQUIRED"));
        assertThat(alarmRepository.count()).isZero();
    }

    @Test
    void complaintIsCreatedOpenWithValidToken() {
        var result = publicOrderService.create(command());

        PassengerComplaintEntity complaint = safetyService.createComplaint(
                result.orderNo(), result.passengerAccessToken(), "SERVICE_ATTITUDE",
                "司机迟到四十分钟且态度恶劣。", "13911112222");

        assertThat(complaint.getComplaintNo()).startsWith("PC");
        assertThat(complaint.getStatus()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(complaint.getOrderNo()).isEqualTo(result.orderNo());
        assertThat(complaint.getHandledBy()).isNull();
    }

    @Test
    void complaintWithInvalidTokenIsRejected() {
        var result = publicOrderService.create(command());

        assertThatThrownBy(() -> safetyService.createComplaint(
                result.orderNo(), "not-a-valid-token", "SERVICE_ATTITUDE", "描述信息足够长。", null))
                .isInstanceOf(BusinessException.class);
        assertThat(complaintRepository.count()).isZero();
    }

    @Test
    void complaintCategoryMustBeWhitelisted() {
        var result = publicOrderService.create(command());

        assertThatThrownBy(() -> safetyService.createComplaint(
                result.orderNo(), result.passengerAccessToken(), "MADE_UP", "描述信息足够长。", null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("COMPLAINT_CATEGORY_INVALID"));
    }

    @Test
    void handleUpdatesStatusAndWritesAuditLog() {
        var result = publicOrderService.create(command());
        PassengerComplaintEntity complaint = safetyService.createComplaint(
                result.orderNo(), result.passengerAccessToken(), "FEE_DISPUTE", "重复收取费用。", null);

        PassengerComplaintEntity handled = safetyService.handle(
                complaint.getComplaintNo(), ComplaintStatus.RESOLVED, "已与司机核实并退还款项。", 9L);

        assertThat(handled.getStatus()).isEqualTo(ComplaintStatus.RESOLVED);
        assertThat(handled.getHandledBy()).isEqualTo(9L);
        assertThat(handled.getHandledAt()).isNotNull();
        assertThat(operationLogRepository.findAll())
                .anySatisfy(log -> {
                    assertThat(log.getObjectType()).isEqualTo("PASSENGER_COMPLAINT");
                    assertThat(log.getObjectId()).isEqualTo(complaint.getComplaintNo());
                });
    }

    @Test
    void handleCannotResetComplaintBackToOpen() {
        var result = publicOrderService.create(command());
        PassengerComplaintEntity complaint = safetyService.createComplaint(
                result.orderNo(), result.passengerAccessToken(), "OTHER", "其他问题描述内容。", null);

        assertThatThrownBy(() -> safetyService.handle(complaint.getComplaintNo(), ComplaintStatus.OPEN, null, 9L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("COMPLAINT_STATUS_INVALID"));
    }

    private PublicOrderService.CreateOrderCommand command() {
        return new PublicOrderService.CreateOrderCommand(
                OrderSourceType.PUBLIC_H5, null, "扬州东站", new BigDecimal("32.391"), new BigDecimal("119.412"),
                "瘦西湖", null, null, 2, Instant.now().plusSeconds(3600), "13800000000", null);
    }
}
