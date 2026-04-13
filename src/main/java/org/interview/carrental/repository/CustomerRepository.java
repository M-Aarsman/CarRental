package org.interview.carrental.repository;

import org.interview.carrental.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByLicenceNumber(String licenceNumber);
    Optional<Customer> findByEmailAndLicenceNumber(String email, String licenceNumber);
}
