package com.capstone.crm.repository;

import com.capstone.crm.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InteractionRepository extends JpaRepository<Interaction, String> {

    List<Interaction> findByCustomerIdOrderByOccurredAtDescInteractionIdDesc(String customerId);
}
