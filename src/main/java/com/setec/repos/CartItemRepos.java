package com.setec.repos;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.CartItems;

public interface CartItemRepos extends JpaRepository<CartItems, Integer> {

    // all lines of one cart (replaces findAll().stream().filter(...))
    List<CartItems> findByCartId(Integer cartId);
}