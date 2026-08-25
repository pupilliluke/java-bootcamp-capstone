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
import jakarta.annotation.PostConstruct;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @PostConstruct
    void seedDemoCustomers() {
        customerRepository.save(new Customer(
                "CUS-1001", "Amina Khan", "amina.khan@example.test", "555-0101",
                CustomerStatus.ACTIVE, LocalDateTime.now()));
        customerRepository.save(new Customer(
                "CUS-1002", "Ravi Singh", "ravi.singh@example.test", "555-0102",
                CustomerStatus.ACTIVE, LocalDateTime.now()));
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

        // Closing a customer is ADMIN-only, and delete() is not the only way to
        // do it. delete() is a soft delete — it just sets CLOSED — so guarding
        // DELETE while leaving this route open to agents guarded the doorway and
        // not the room: an agent could pick CLOSED in the edit form's status
        // dropdown and reach exactly the same state through PUT. The rule lives
        // here rather than in SecurityConfig because it depends on the request
        // body, which URL matchers cannot see.
        if (request.status() == CustomerStatus.CLOSED
                && customer.getStatus() != CustomerStatus.CLOSED
                && !callerIsAdmin()) {
            // Phrased for a person, not a log: GlobalExceptionHandler puts this
            // straight into the response body and the edit form renders it.
            throw new AccessDeniedException("Only an administrator can close a customer");
        }

        CustomerMapper.applyUpdate(customer, request);
        Customer saved = customerRepository.save(customer);
        return CustomerMapper.toResponse(saved);
    }

    // Only the transition into CLOSED is restricted. An update that leaves an
    // already-closed customer closed is an ordinary edit of its name or email,
    // and refusing that would make closed records uneditable by anyone.
    private static boolean callerIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    public void delete(String customerId) {     //soft delete. just set to closed
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        customer.setStatus(CustomerStatus.CLOSED);
        customerRepository.save(customer);
    }
}
