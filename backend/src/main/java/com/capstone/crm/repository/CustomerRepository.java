package com.capstone.crm.repository;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Backs the status filter on GET /api/customers. Derived from the method
    // name rather than written as @Query: "WHERE status IN (:statuses)" is the
    // entire statement, so a hand-written one would add a string to keep in
    // step with the field name and buy nothing.
    //
    // Collection rather than Set in the signature, so callers are free to pass
    // an EnumSet -- which is what CustomerService's default is -- without the
    // parameter type deciding that for them.
    List<Customer> findByStatusIn(Collection<CustomerStatus> statuses);

    // The paged form of the same filter, for GET /api/customers?page=&size=.
    // Both exist because the unpaged one still backs the places that genuinely
    // want the whole filtered set; the controller no longer does.
    Page<Customer> findByStatusIn(Collection<CustomerStatus> statuses, Pageable pageable);

    // Status filter plus the list page's search box, in one query.
    //
    // Written out rather than derived: the derived name for this would be
    // findByStatusInAndFullNameContainingIgnoreCaseOrStatusInAndCustomerIdContaining...
    // repeating the status clause once per searched column, which is both
    // unreadable and wrong -- OR binds looser than the reader expects and the
    // status filter stops applying to every branch.
    //
    // The search has to run in the database, not the browser. Filtering a page
    // client-side searches only the rows that page happens to hold, so typing a
    // name that exists on page nine finds nothing from page one.
    @Query("""
            SELECT c FROM Customer c
            WHERE c.status IN :statuses
              AND (
                LOWER(c.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.customerId) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Customer> search(Collection<CustomerStatus> statuses, String q, Pageable pageable);
}
