package org.interview.carrental.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {
    @Id
    private UUID id;
    private String name;
    private String email;
    @Column(unique = true)
    private String licenceNumber;

    public static Customer create(String name, String email, String licenceNumber) {
        Customer customer = new Customer();
        customer.id = UUID.randomUUID();
        customer.name = name;
        customer.email = email;
        customer.licenceNumber = licenceNumber;
        return customer;
    }
}
