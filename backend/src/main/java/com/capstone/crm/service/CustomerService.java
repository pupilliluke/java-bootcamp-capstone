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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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

    // Ids are canonicalised here, at the only door into the repository, and
    // ck_customer_id_upper in V3__customer.sql backs it up. A primary key is
    // case-sensitive, so without this "cus-1006" and "CUS-1006" are separate
    // customers that look identical to a person reading the screen.
    //
    // Trimmed as well as upper-cased: a trailing space is the same class of
    // invisible difference, and pasting an id into a form is how it gets there.
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
        log.info("Updated customer {} (status {} -> {})", customerId, previousStatus, saved.getStatus());
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
        Customer customer = customerRepository.findById(canonical(customerId))
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found: " + customerId));
        customer.setStatus(CustomerStatus.CLOSED);
        customerRepository.save(customer);
        log.info("Closed customer {}", customerId);
    }
}
