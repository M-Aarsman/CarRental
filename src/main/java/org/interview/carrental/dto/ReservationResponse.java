package org.interview.carrental.dto;

import org.interview.carrental.model.CarType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationResponse (
        UUID reservationId,
        String carBrand,
        String carModel,
        CarType carType,
        LocalDateTime start,
        LocalDateTime end,
        BigDecimal totalPrice,
        String customerName ) {}
