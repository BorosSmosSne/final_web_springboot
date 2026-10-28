package com.setec.repos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.Order;

public interface OrderRepos extends JpaRepository<Order, Integer> {

}