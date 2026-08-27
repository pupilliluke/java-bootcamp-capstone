package com.capstone.crm.api;

import com.capstone.crm.api.dto.CustomerRequestDTO;
import com.capstone.crm.api.dto.CustomerResponseDTO;
import com.capstone.crm.api.dto.PageResponse;
import com.capstone.crm.api.dto.CustomerUpdateDTO;
import com.capstone.crm.entity.CustomerStatus;
import com.capstone.crm.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDTO> create(@Valid @RequestBody CustomerRequestDTO request) {
        CustomerResponseDTO created = customerService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponseDTO> get(@PathVariable String customerId) {
        return ResponseEntity.ok(customerService.get(customerId));
    }

    /**
     * A repeatable parameter, so several statuses can be asked for at once:
     * {@code ?status=ACTIVE&status=PROSPECT}. Omitting it means every status
     * except CLOSED.
     *
     * An unknown value is a 400 rather than a 500 with no work here: Spring
     * cannot convert "VIP" to a CustomerStatus and raises
     * MethodArgumentTypeMismatchException, which GlobalExceptionHandler already
     * answers as Bad Request. CustomerControllerTest pins that, since it is a
     * behaviour this endpoint depends on rather than one it states.
     */
    /**
     * One page of customers.
     *
     * <p>Every parameter has a default, so {@code GET /api/v1/customers} still
     * answers without any of them -- but it now answers with the first page
     * rather than the whole book. size is capped in the service; sort is an
     * allow-list and an unknown property is a 400.
     */
    @GetMapping
    public ResponseEntity<PageResponse<CustomerResponseDTO>> list(
            @RequestParam(name = "status", required = false) Set<CustomerStatus> status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "direction", required = false) String direction) {
        return ResponseEntity.ok(customerService.list(status, q, page, size, sort, direction));
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerResponseDTO> update(@PathVariable String customerId,
            @Valid @RequestBody CustomerUpdateDTO request) {
        return ResponseEntity.ok(customerService.update(customerId, request));
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> delete(@PathVariable String customerId) {
        customerService.delete(customerId);
        return ResponseEntity.noContent().build();
    }
}