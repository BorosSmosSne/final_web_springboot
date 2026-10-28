package com.setec.repos;

import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.Cart;

public interface CartRepos extends JpaRepository<Cart, Integer>{


}
