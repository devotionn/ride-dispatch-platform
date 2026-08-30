package com.funccrypto.ridedispatch.order.api;

import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.payment.PaymentService;
import com.funccrypto.ridedispatch.place.PlaceCatalogService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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

    private static final String PASSENGER_TOKEN_HEADER = "X-Passenger-Token";
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PublicOrderService service;
    private final PaymentService paymentService;
    private final PlaceCatalogService placeCatalogService;

    public PublicOrderController(PublicOrderService service, PaymentService paymentService, PlaceCatalogService placeCatalogService) {
        this.service = service;
        this.paymentService = paymentService;
        this.placeCatalogService = placeCatalogService;
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
        placeCatalogService.recordUseIfEnabled(request.pickup().placeId());
        placeCatalogService.recordUseIfEnabled(request.destination().placeId());
        return new CreateOrderResponse(result.orderNo(), result.status(), result.passengerAccessToken());
    }

    @GetMapping("/{orderNo}")
    PassengerOrderResponse get(
            @PathVariable String orderNo,
            @RequestHeader(PASSENGER_TOKEN_HEADER) String passengerToken) {
        var order = service.getForPassenger(orderNo, passengerToken);
        var payment = paymentService.findByOrderId(order.getId()).orElse(null);
        String paymentToken = payment == null ? null : paymentService.issueFreshToken(payment, java.time.Instant.now());
        return PassengerOrderResponse.from(order, payment, paymentToken);
    }

    @PostMapping("/{orderNo}/cancel")
    OrderStatus cancel(
            @PathVariable String orderNo,
            @RequestHeader(PASSENGER_TOKEN_HEADER) String passengerToken) {
        return service.cancel(orderNo, passengerToken);
    }
}
