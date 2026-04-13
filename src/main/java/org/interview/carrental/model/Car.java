package org.interview.carrental.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class  Car {
    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    private CarType type;
    private String brand;
    private String model;

    public static Car create(CarType type, String brand, String model) {
        Car car = new Car();
        car.id = UUID.randomUUID();
        car.type = type;
        car.brand = brand;
        car.model = model;
        return car;
    }
}
