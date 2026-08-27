package com.capstone.crm.repository;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    // Backs the "CUS-" numbers CustomerService.create() assigns. A database
    // sequence rather than count()+1: counting breaks the moment a customer is
    // deleted, since the next created customer would reuse a freed number.
    @Query(value = "SELECT nextval('customer_number_seq')", nativeQuery = true)
    long nextCustomerNumber();

    // Backs the status filter on GET /api/v1/customers. Derived from the method
    // name rather than written as @Query: "WHERE status IN (:statuses)" is the
    // entire statement, so a hand-written one would add a string to keep in
    // step with the field name and buy nothing.
    //
    // Collection rather than Set in the signature, so callers are free to pass
    // an EnumSet -- which is what CustomerService's default is -- without the
    // parameter type deciding that for them.
    List<Customer> findByStatusIn(Collection<CustomerStatus> statuses);
}
