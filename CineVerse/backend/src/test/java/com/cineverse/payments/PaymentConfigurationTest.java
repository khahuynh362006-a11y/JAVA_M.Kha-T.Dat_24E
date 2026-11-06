package com.cineverse.payments;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cineverse.booking.BookingService;
import com.cineverse.email.EmailService;
import com.cineverse.shared.*;
import com.cineverse.users.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

class PaymentConfigurationTest {
    @Test
    void incompleteSandboxDoesNotCreateOrderOrTouchHold() {
        var momo = mock(MomoSandboxPaymentGateway.class);
        var orders = mock(PaymentOrderRepository.class);
        var bookings = mock(BookingService.class);
        var env = mock(Environment.class);
        when(env.matchesProfiles("dev", "test")).thenReturn(true);
        when(momo.provider()).thenReturn("MOMO_SANDBOX");
        var service =
                new PaymentService(
                        orders,
                        mock(SubscriptionPlanRepository.class),
                        mock(UserRepository.class),
                        momo,
                        mock(VnpaySandboxPaymentGateway.class),
                        mock(PayosPaymentGateway.class),
                        List.of(momo),
                        mock(JdbcTemplate.class),
                        mock(Audit.class),
                        mock(EmailService.class),
                        bookings,
                        "momo",
                        env);
        assertFalse(service.configuration().ready());
        assertEquals("MOMO_SANDBOX", service.configuration().provider());
        assertEquals(
                "MOMO_NOT_CONFIGURED",
                assertThrows(ApiException.class, () -> service.createForBooking(1L, "booking-1"))
                        .code);
        verifyNoInteractions(orders, bookings);
    }
}
