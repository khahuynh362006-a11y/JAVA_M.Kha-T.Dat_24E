package com.cineverse.payments;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {}
