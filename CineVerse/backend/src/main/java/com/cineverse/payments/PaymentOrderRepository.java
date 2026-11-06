package com.cineverse.payments;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, String> {
    Optional<PaymentOrder> findByBookingId(String bookingId);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PaymentOrder o where o.id=:id")
    Optional<PaymentOrder> lockById(@Param("id") String id);

    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PaymentOrder> findTop20ByProviderAndStatusAndCreatedAtBeforeOrderByCreatedAtAsc(
            String provider, String status, Instant before);
}
