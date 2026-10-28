package com.setec.repos;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.CartItems;

public interface CartItemRepos extends JpaRepository<CartItems, Integer>{

    Optional<CartItems> findByCartIdAndProductId(
            Integer cartId, Integer productId);

}
