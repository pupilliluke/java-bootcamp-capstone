package com.capstone.crm.service;

import com.capstone.crm.api.dto.CustomerMapper;
import com.capstone.crm.api.dto.CustomerRequestDTO;
import com.capstone.crm.api.dto.CustomerResponseDTO;
import com.capstone.crm.api.dto.CustomerUpdateDTO;
import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import com.capstone.crm.exception.CustomerNotFoundException;
import com.capstone.crm.exception.DuplicateCustomerException;
import com.capstone.crm.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

   //make ids uppercase
    private static String canonical(String customerId) {
        return customerId == null ? null : customerId.trim().toUpperCase();
    }

    public CustomerResponseDTO create(CustomerRequestDTO request) {
        String customerId = canonical(request.customerId());
        if (customerRepository.existsById(customerId)) {
            throw new DuplicateCustomerException("Duplicate customer: " + customerId);
        }
        Customer customer = CustomerMapper.toEntity(request);
        customer.setCustomerId(customerId);
        customer.setCreatedAt(Instant.now());
        Customer saved = customerRepository.save(customer);
        log.info("Created customer {}", saved.getCustomerId());
        return CustomerMapper.toResponse(saved);
    }

    public CustomerResponseDTO get(String customerId) {
        Customer customer = customerRepository.findById(canonical(customerId))
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        return CustomerMapper.toResponse(customer);
    }

    public List<CustomerResponseDTO> list() {
        return customerRepository.findAll().stream()
                .map(CustomerMapper::toResponse)
                .collect(Collectors.toList());
    }

    public CustomerResponseDTO update(String customerId, CustomerUpdateDTO request) {
        Customer customer = customerRepository.findById(canonical(customerId))
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        CustomerStatus previousStatus = customer.getStatus();

        CustomerMapper.applyUpdate(customer, request);
        Customer saved = customerRepository.save(customer);
        log.info("Updated customer {} (status {} -> {})", customerId, previousStatus, saved.getStatus());
        return CustomerMapper.toResponse(saved);
    }

    //hard delete now.
    public void delete(String customerId) {
        Customer customer = customerRepository.findById(canonical(customerId))
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        customerRepository.delete(customer);
        log.info("Deleted customer {}", customerId);
    }
}
