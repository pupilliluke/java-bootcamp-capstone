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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public CustomerResponseDTO create(CustomerRequestDTO request) {
        if (customerRepository.existsById(request.customerId())) {
            throw new DuplicateCustomerException("Duplicate customer: " + request.customerId());
        }
        Customer customer = CustomerMapper.toEntity(request);
        customer.setCreatedAt(LocalDateTime.now());
        Customer saved = customerRepository.save(customer);
        return CustomerMapper.toResponse(saved);
    }

    public CustomerResponseDTO get(String customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        return CustomerMapper.toResponse(customer);
    }

    public List<CustomerResponseDTO> list() {
        return customerRepository.findAll().stream()
                .map(CustomerMapper::toResponse)
                .collect(Collectors.toList());
    }

    public CustomerResponseDTO update(String customerId, CustomerUpdateDTO request) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        CustomerMapper.applyUpdate(customer, request);
        Customer saved = customerRepository.save(customer);
        return CustomerMapper.toResponse(saved);
    }

    public void delete(String customerId) {     //soft delete. just set to closed
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        customer.setStatus(CustomerStatus.CLOSED);
        customerRepository.save(customer);
    }
}
