package com.capstone.crm.service;

import com.capstone.crm.api.dto.CustomerMapper;
import com.capstone.crm.api.dto.CustomerRequestDTO;
import com.capstone.crm.api.dto.CustomerResponseDTO;
import com.capstone.crm.api.dto.CustomerUpdateDTO;
import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import com.capstone.crm.exception.CustomerNotFoundException;
import com.capstone.crm.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    // What GET /api/v1/customers answers with when the caller names no status.
    //
    // complementOf rather than an explicit EnumSet.of(ACTIVE, SUSPENDED,
    // PROSPECT): a status added to CustomerStatus later then joins the default
    // list on its own. Spelled out, a new status would be missing from every
    // list page until somebody remembered this constant existed -- and the
    // symptom of that is customers silently absent, which is the hardest kind
    // of bug to notice.
    private static final Set<CustomerStatus> DEFAULT_STATUSES =
            EnumSet.complementOf(EnumSet.of(CustomerStatus.CLOSED));

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

   //make ids uppercase
    private static String canonical(String customerId) {
        return customerId == null ? null : customerId.trim().toUpperCase();
    }

    //now has customerId assigned to it. no need for duplicate checks now.
    public CustomerResponseDTO create(CustomerRequestDTO request) {
        String customerId = "CUS-" + customerRepository.nextCustomerNumber();
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

    /**
     * Naming no status means every status except CLOSED, which is what the list
     * is for day to day. This filters the list, it does not hide the record: a
     * closed customer is still reachable by asking for it explicitly
     * (?status=CLOSED) and by id through {@link #get(String)}.
     *
     * The filter is applied by the database rather than by discarding rows
     * here, so the query returns what was asked for instead of everything.
     */
    public List<CustomerResponseDTO> list(Set<CustomerStatus> statuses) {
        Set<CustomerStatus> effective =
                (statuses == null || statuses.isEmpty()) ? DEFAULT_STATUSES : statuses;
        return customerRepository.findByStatusIn(effective).stream()
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
