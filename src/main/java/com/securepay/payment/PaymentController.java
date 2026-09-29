package com.securepay.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Create and retrieve customer payments")
@SecurityRequirement(name = "basicAuth")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Create a payment", description = "Creates a pending payment for the authenticated customer. Reuse the idempotency key to safely retry the same request.")
    public PaymentResponse create(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        return PaymentResponse.from(paymentService.create(request, authentication.getName(), idempotencyKey));
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get a payment", description = "Returns a payment belonging to the authenticated customer.")
    public PaymentResponse get(@PathVariable UUID paymentId, Authentication authentication) {
        return PaymentResponse.from(paymentService.getForCustomer(paymentId, authentication.getName()));
    }
}
