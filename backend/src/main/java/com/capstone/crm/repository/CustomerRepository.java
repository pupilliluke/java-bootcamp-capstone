package com.capstone.crm.repository;

import com.capstone.crm.entity.Customer;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

//TODO: replace with a Spring Data JPA repository .
@Repository
public class CustomerRepository {

    private final Map<String, Customer> store = new ConcurrentHashMap<>();

    public Customer save(Customer customer) {
        store.put(customer.getCustomerId(), customer);
        return customer;
    }

    public Optional<Customer> findById(String customerId) {
        return Optional.ofNullable(store.get(customerId));
    }

    public boolean existsById(String customerId) {
        return store.containsKey(customerId);
    }

    public List<Customer> findAll() {
        return new ArrayList<>(store.values());
    }
}