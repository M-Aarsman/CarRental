package org.interview.carrental.repository;

import org.interview.carrental.model.CarType;
import org.interview.carrental.model.Reservation;
import org.interview.carrental.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByCarId(UUID carId);
    List<Reservation> findByCarIdAndStatusNot(UUID carId, ReservationStatus status);
    List<Reservation> findByCarTypeAndStatusNot(CarType carType, ReservationStatus status);
}
