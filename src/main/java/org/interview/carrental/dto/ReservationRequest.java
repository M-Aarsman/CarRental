package org.interview.carrental.dto;

import org.interview.carrental.model.CarType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReservationRequest (
        UUID carId,
        CarType carType,

        String customerName,
        String email,
        String licenseNumber,

        LocalDateTime start,
        int days) {}
