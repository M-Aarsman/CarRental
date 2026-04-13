package org.interview.carrental.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {
    @Id
    private UUID id;
    @ManyToOne
    private Car car;
    @ManyToOne
    private Customer customer;
    @Column(name = "start_time")
    private LocalDateTime start;
    @Column(name = "end_time")
    private LocalDateTime end;
    @Enumerated(EnumType.STRING)
    private ReservationStatus status;
    private BigDecimal totalPrice;

    public static Reservation create(
            Car car,
            Customer customer,
            LocalDateTime start,
            LocalDateTime end,
            BigDecimal totalPrice
    ) {
        Reservation reservation = new Reservation();
        reservation.id = UUID.randomUUID();
        reservation.car = car;
        reservation.customer = customer;
        reservation.start = start;
        reservation.end = end;
        reservation.status = ReservationStatus.RESERVED;
        reservation.totalPrice = totalPrice;
        return reservation;
    }

    public void cancel() {
        if (this.status == ReservationStatus.CANCELLED) {
            return;
        }
        this.status = ReservationStatus.CANCELLED;
    }

    public void complete(LocalDateTime end) {
        if (this.status == ReservationStatus.COMPLETED) {
            return;
        }
        this.end = end;
        this.status = ReservationStatus.COMPLETED;
    }

    public boolean isActive() {
        return this.status == ReservationStatus.RESERVED;
    }
}
