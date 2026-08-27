package com.capstone.crm.repository;

import com.capstone.crm.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    // Backs the "CUS-" numbers CustomerService.create() assigns. A database
    // sequence rather than count()+1: counting breaks the moment a customer is
    // deleted, since the next created customer would reuse a freed number.
    @Query(value = "SELECT nextval('customer_number_seq')", nativeQuery = true)
    long nextCustomerNumber();
}
