package com.capstone.crm.service;

import com.capstone.crm.entity.Customer;
import com.capstone.crm.entity.CustomerStatus;
import com.capstone.crm.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

//TODO: demo records only. Replace with real customer onboarding before any
// deployment that is not a training environment.
@Component
public class DemoCustomerSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCustomerSeeder.class);

    private final CustomerRepository customerRepository;

    public DemoCustomerSeeder(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // Guarded by existsById rather than always saving, so a restart cannot
    // clobber a customer a demo session has since updated or closed.
    @Override
    public void run(ApplicationArguments args) {
        seed("CUS-1001", "Amina Khan", "amina.khan@example.test", "555-0101", CustomerStatus.ACTIVE);
        seed("CUS-1002", "Ravi Singh", "ravi.singh@example.test", "555-0102", CustomerStatus.ACTIVE);
    }

    private void seed(String customerId, String fullName, String email, String phone, CustomerStatus status) {
        if (customerRepository.existsById(customerId)) {
            return;
        }
        customerRepository.save(new Customer(customerId, fullName, email, phone, status, LocalDateTime.now()));
        log.info("Seeded demo customer {}", customerId);
    }
}
