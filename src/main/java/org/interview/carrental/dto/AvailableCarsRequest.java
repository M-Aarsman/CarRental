package org.interview.carrental.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.interview.carrental.model.CarType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableCarsRequest {
    private List<CarType> types;
    private String brand;
    private String model;
    private UUID carId;
    private LocalDateTime start;
    private int days;
}
