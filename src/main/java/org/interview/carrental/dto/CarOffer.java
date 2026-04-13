package org.interview.carrental.dto;

import org.interview.carrental.model.CarType;

import java.math.BigDecimal;
import java.util.UUID;

public record CarOffer(
        UUID carId,
        CarType type,
        String brand,
        String model,
        BigDecimal pricePerDay,
        BigDecimal totalPrice
) {
}
