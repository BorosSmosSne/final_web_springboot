
package com.setec.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.setec.entities.Cart;
import com.setec.repos.CartRepos;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartRepos cartRepos;

    public CartController(CartRepos cartRepos) {
        this.cartRepos = cartRepos;
    }

    // Get all carts
    @GetMapping
    public Object getAll() {
        return cartRepos.findAll();
    }

    // Create a new cart
    @PostMapping
    public Object createCart() {
        Cart cart = new Cart();
        return cartRepos.save(cart);
    }

    // Get a cart by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getById(
            @PathVariable("id") Integer id) {

        var cart = cartRepos.findById(id);

        if (cart.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart not found"));
        }

        return ResponseEntity.ok(cart.get());
    }
 // Delete a cart by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCart(
            @PathVariable("id") Integer id) {

        var cart = cartRepos.findById(id);

        if (cart.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart not found"));
        }

        cartRepos.delete(cart.get());

        return ResponseEntity.ok(
                Map.of("message", "Cart deleted successfully")
        );
    }
}
