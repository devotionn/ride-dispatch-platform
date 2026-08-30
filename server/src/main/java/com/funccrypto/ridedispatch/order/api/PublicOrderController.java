package com.funccrypto.ridedispatch.order.api;

import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.driver.VehicleRepository;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.payment.PaymentService;
import com.funccrypto.ridedispatch.place.PlaceCatalogService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/orders")
public class PublicOrderController {

    private static final Logger log = LoggerFactory.getLogger(PublicOrderController.class);

    private static final String PASSENGER_TOKEN_HEADER = "X-Passenger-Token";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PublicOrderService service;
    private final PaymentService paymentService;
    private final PlaceCatalogService placeCatalogService;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    public PublicOrderController(
            PublicOrderService service,
            PaymentService paymentService,
            PlaceCatalogService placeCatalogService,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository) {
        this.service = service;
        this.paymentService = paymentService;
        this.placeCatalogService = placeCatalogService;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateOrderResponse create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {
        PublicOrderService.CreateOrderResult result = service.create(new PublicOrderService.CreateOrderCommand(
                request.sourceType(),
                request.driverShortCode(),
                request.pickup().address(),
                request.pickup().latitude(),
                request.pickup().longitude(),
                request.destination().address(),
                request.destination().latitude(),
                request.destination().longitude(),
                request.passengerCount(),
                request.departureAt().toInstant(),
                request.mobile(),
                request.remark()), idempotencyKey);
        // Catalog statistics are intentionally best-effort and outside the order transaction.
        // Do not count an idempotent replay as a second place selection.
        if (result.newlyCreated()) {
            recordPlaceUse(
                    request.pickup().placeId(), request.pickup().address(),
                    request.pickup().latitude(), request.pickup().longitude());
            recordPlaceUse(
                    request.destination().placeId(), request.destination().address(),
                    request.destination().latitude(), request.destination().longitude());
        }
        return new CreateOrderResponse(result.orderNo(), result.status(), result.passengerAccessToken());
    }

    private void recordPlaceUse(Long placeId, String addressText, java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        try {
            placeCatalogService.recordUseIfEnabled(
                    placeId, addressText, latitude, longitude);
        } catch (RuntimeException exception) {
            // Analytics must never turn a committed order into a failed response,
            // including failures raised while the REQUIRES_NEW transaction commits.
            log.warn("Unable to record place catalog usage", exception);
        }
    }

    @GetMapping("/{orderNo}")
    PassengerOrderResponse get(
            @PathVariable String orderNo,
            @RequestHeader(PASSENGER_TOKEN_HEADER) String passengerToken) {
        var order = service.getForPassenger(orderNo, passengerToken);
        var payment = paymentService.findByOrderId(order.getId()).orElse(null);
        String paymentToken = payment == null ? null : paymentService.issueFreshToken(payment, java.time.Instant.now());
        // Driver/vehicle context powers the safety center (alarm page needs
        // plate and name to hand to the police).
        var driver = order.getCurrentDriverId() == null
                ? null
                : driverRepository.findById(order.getCurrentDriverId()).orElse(null);
        var vehicle = driver == null || driver.getDefaultVehicleId() == null
                ? null
                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null);
        return PassengerOrderResponse.from(order, payment, paymentToken, driver, vehicle);
    }

    @PostMapping("/{orderNo}/cancel")
    OrderStatus cancel(
            @PathVariable String orderNo,
            @RequestHeader(PASSENGER_TOKEN_HEADER) String passengerToken) {
        return service.cancel(orderNo, passengerToken);
    }
}
