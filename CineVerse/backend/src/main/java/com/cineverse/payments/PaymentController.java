package com.cineverse.payments;

import com.cineverse.auth.AuthDtos;
import com.cineverse.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private final PaymentService payments;
    private final CurrentUser current;

    public PaymentController(PaymentService payments, CurrentUser current) {
        this.payments = payments;
        this.current = current;
    }

    public record CreateOrder(@NotBlank String bookingId) {}

    public record Simulate(@Pattern(regexp = "success|failure|cancel") @NotNull String outcome) {}

    @GetMapping("/payment-config")
    public PaymentService.Configuration config() {
        return payments.configuration();
    }

    @GetMapping("/subscription-plans")
    public Map<String, Object> plans() {
        return Map.of("plans", payments.plans(), "mode", payments.mode());
    }

    @PostMapping({"/payments", "/payments/momo/create"})
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentService.OrderView create(@Valid @RequestBody CreateOrder in) {
        return payments.createForBooking(current.id(), in.bookingId());
    }

    @PostMapping("/payments/momo/ipn")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ipn(@RequestBody Map<String, Object> in) {
        payments.ipn(in);
    }

    @PostMapping("/payments/{id}/simulate")
    public PaymentService.OrderView simulate(
            @PathVariable String id, @Valid @RequestBody Simulate in) {
        return payments.simulate(current.id(), id, in.outcome());
    }

    @GetMapping({"/payments/{id}", "/payments/{id}/status"})
    public PaymentService.OrderView order(@PathVariable String id) {
        var u = current.get();
        return payments.get(u.id, id, u.role.equals("ADMIN"));
    }

    @GetMapping("/users/me/payments")
    public List<PaymentService.OrderView> mine() {
        return payments.mine(current.id());
    }

    @GetMapping({"/subscriptions/me", "/users/me/subscription"})
    public AuthDtos.UserView subscription() {
        return AuthDtos.view(current.get());
    }
}
