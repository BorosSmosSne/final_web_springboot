
package com.setec.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.setec.entities.CartItems;
import com.setec.repos.CartItemRepos;
import com.setec.repos.CartRepos;
import com.setec.repos.ProductRepo;

@RestController
@RequestMapping("/api/cart-items")
public class CartItemsController {

    private final CartItemRepos cartItemRepos;
    private final CartRepos cartRepos;
    private final ProductRepo productRepo;

    public CartItemsController(
            CartItemRepos cartItemRepos,
            CartRepos cartRepos,
            ProductRepo productRepo) {

        this.cartItemRepos = cartItemRepos;
        this.cartRepos = cartRepos;
        this.productRepo = productRepo;
    }

    // Get all items belonging to one cart
    @GetMapping("/cart/{cartId}")
    public Object getItemsByCart(
            @PathVariable("cartId") Integer cartId) {

        var cart = cartRepos.findById(cartId);

        if (cart.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart not found"));
        }

        return cartItemRepos.findAll().stream()
                .filter(item ->
                        item.getCart().getId().equals(cartId))
                .toList();
    }

    // Add a product to a cart
    @PostMapping("/{cartId}/products/{productId}")
    public ResponseEntity<?> addToCart(
            @PathVariable("cartId") Integer cartId,
            @PathVariable("productId") Integer productId) {

        var cart = cartRepos.findById(cartId);

        if (cart.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart not found"));
        }

        var product = productRepo.findById(productId);

        if (product.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Product not found"));
        }

        var existingItem =
                cartItemRepos.findByCartIdAndProductId(
                        cartId, productId);

        if (existingItem.isPresent()) {
            CartItems item = existingItem.get();
            item.setQuantity(item.getQuantity() + 1);

            return ResponseEntity.ok(
                    cartItemRepos.save(item));
        }

        CartItems item = new CartItems();
        item.setCart(cart.get());
        item.setProduct(product.get());
        item.setQuantity(1);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cartItemRepos.save(item));
    }

    // Increase quantity
    @PutMapping("/{id}/increase")
    public ResponseEntity<?> increase(
            @PathVariable("id") Integer id) {

        var item = cartItemRepos.findById(id);

        if (item.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart item not found"));
        }

        CartItems cartItem = item.get();
        cartItem.setQuantity(cartItem.getQuantity() + 1);

        return ResponseEntity.ok(
                cartItemRepos.save(cartItem));
    }

    // Decrease quantity
    @PutMapping("/{id}/decrease")
    public ResponseEntity<?> decrease(
            @PathVariable("id") Integer id) {

        var item = cartItemRepos.findById(id);

        if (item.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart item not found"));
        }

        CartItems cartItem = item.get();

        if (cartItem.getQuantity() > 1) {
            cartItem.setQuantity(cartItem.getQuantity() - 1);

            return ResponseEntity.ok(
                    cartItemRepos.save(cartItem));
        }

        // Quantity is 1, so remove the item
        cartItemRepos.delete(cartItem);

        return ResponseEntity.ok(
                Map.of("message", "Product removed from cart"));
    }

    // Remove an item completely
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeItem(
            @PathVariable("id") Integer id) {

        var item = cartItemRepos.findById(id);

        if (item.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart item not found"));
        }

        cartItemRepos.delete(item.get());

        return ResponseEntity.ok(
                Map.of("message", "Cart item removed successfully"));
    }
    
 // Added: Empty all items from a cart (DELETE /api/cart-items/cart/{cartId}/clear)
    @DeleteMapping("/cart/{cartId}/clear")
    public Object clearCart(@PathVariable("cartId") Integer cartId) {
        var cart = cartRepos.findById(cartId);
        if (cart.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Cart not found"));
        }

        var items = cartItemRepos.findAll().stream()
                .filter(item -> item.getCart().getId().equals(cartId))
                .toList();

        if (items.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Cart is already empty"));
        }

        cartItemRepos.deleteAll(items);
        return ResponseEntity.ok(Map.of("message", "All items cleared from cart " + cartId));
    }
}
