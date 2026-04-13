package org.interview.carrental.repository;

import org.interview.carrental.model.Car;
import org.interview.carrental.model.CarType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {
    List<Car> findByType(CarType type);
    List<Car> findByTypeIn(List<CarType> types);
}
