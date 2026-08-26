package com.capstone.crm.api.dto;

import com.capstone.crm.entity.Customer;

public final class CustomerMapper {

    private CustomerMapper() {}

    // customerId is not set here
    // set after this call from customer_number_seq, never by the caller
    // still CUS-XXXX
    public static Customer toEntity(CustomerRequestDTO dto) {
        Customer customer = new Customer();
        customer.setFullName(dto.fullName());
        customer.setEmail(dto.email());
        customer.setPhone(dto.phone());
        customer.setStatus(dto.status());
        return customer;
    }

    public static void applyUpdate(Customer customer, CustomerUpdateDTO dto) {
        customer.setFullName(dto.fullName());
        customer.setEmail(dto.email());
        customer.setPhone(dto.phone());
        customer.setStatus(dto.status());
    }

    public static CustomerResponseDTO toResponse(Customer customer) {
        return new CustomerResponseDTO(
                customer.getCustomerId(),
                customer.getFullName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getStatus(),
                customer.getCreatedAt()
        );
    }
}