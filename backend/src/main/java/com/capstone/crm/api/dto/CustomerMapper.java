package com.capstone.crm.api.dto;

import com.capstone.crm.entity.Customer;

public final class CustomerMapper {

    private CustomerMapper() {}

    public static Customer toEntity(CustomerRequestDTO dto) {
        Customer customer = new Customer();
        customer.setCustomerId(dto.customerId());
        customer.setFullName(dto.fullName());
        customer.setEmail(dto.email());
        customer.setPhone(dto.phone());
        customer.setStatus(dto.status());
        return customer;
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