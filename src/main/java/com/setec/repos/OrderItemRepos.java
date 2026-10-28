package com.setec.repos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.OrderItems;

public interface OrderItemRepos extends JpaRepository<OrderItems, Integer> {

}