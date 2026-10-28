package com.setec.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.setec.entities.CartItems;
import com.setec.repos.CartItemRepos;
import com.setec.repos.CartRepos;
import com.setec.repos.ProductRepo;

@RestController
@RequestMapping("/api/cart-items")
public class CartItemsController {

    private static final List<String> SIZES = List.of("S", "M", "L");
    private static final List<String> ICE = List.of("NONE", "LESS", "NORMAL");

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

    private static ResponseEntity<?> msg(HttpStatus status, String text) {
        return ResponseEntity.status(status).body(Map.of("message", text));
    }

    // Get all items belonging to one cart
    @GetMapping("/cart/{cartId}")
    public Object getItemsByCart(@PathVariable("cartId") Integer cartId) {

        if (cartRepos.findById(cartId).isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Cart not found");
        }
        return cartItemRepos.findByCartId(cartId);
    }

    /**
     * Add a drink to the cart.
     * Body is OPTIONAL. Example:
     * { "size": "L", "sugar": 50, "ice": "LESS", "note": "no straw", "quantity": 1 }
     * Same product + same options  -> quantity goes up.
     * Same product + other options -> a separate line.
     */
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = false,
            content = @io.swagger.v3.oas.annotations.media.Content(
                    mediaType = "application/json",
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Large iced latte",
                            value = """
                            {"size":"L","sugar":50,"ice":"LESS","note":"no straw","quantity":1}
                            """)))
    @PostMapping("/{cartId}/products/{productId}")
    public ResponseEntity<?> addToCart(
            @PathVariable("cartId") Integer cartId,
            @PathVariable("productId") Integer productId,
            @RequestBody(required = false) Map<String, Object> body) {

        var cart = cartRepos.findById(cartId);
        if (cart.isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Cart not found");
        }

        var product = productRepo.findById(productId);
        if (product.isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (!product.get().getAvailable()) {
            return msg(HttpStatus.CONFLICT, product.get().getName() + " is sold out");
        }

        // ----- read + validate options -----
        String size = null;
        Integer sugar = null;
        String ice = null;
        String note = null;
        int quantity = 1;

        if (body != null) {
            Object s = body.get("size");
            if (s != null && !s.toString().isBlank()) {
                size = s.toString().trim().toUpperCase();
                if (!SIZES.contains(size)) {
                    return msg(HttpStatus.BAD_REQUEST, "size must be S, M or L");
                }
            }

            Object su = body.get("sugar");
            if (su != null && !su.toString().isBlank()) {
                try {
                    sugar = Integer.valueOf(su.toString().trim());
                } catch (NumberFormatException e) {
                    return msg(HttpStatus.BAD_REQUEST, "sugar must be a number from 0 to 100");
                }
                if (sugar < 0 || sugar > 100) {
                    return msg(HttpStatus.BAD_REQUEST, "sugar must be a number from 0 to 100");
                }
            }

            Object ic = body.get("ice");
            if (ic != null && !ic.toString().isBlank()) {
                ice = ic.toString().trim().toUpperCase();
                if (!ICE.contains(ice)) {
                    return msg(HttpStatus.BAD_REQUEST, "ice must be NONE, LESS or NORMAL");
                }
            }

            Object n = body.get("note");
            if (n != null && !n.toString().isBlank()) {
                note = n.toString().trim();
            }

            Object q = body.get("quantity");
            if (q != null && !q.toString().isBlank()) {
                try {
                    quantity = Integer.parseInt(q.toString().trim());
                } catch (NumberFormatException e) {
                    return msg(HttpStatus.BAD_REQUEST, "quantity must be a number from 1 to 50");
                }
                if (quantity < 1 || quantity > 50) {
                    return msg(HttpStatus.BAD_REQUEST, "quantity must be a number from 1 to 50");
                }
            }
        }

        // ----- same product AND same options? then just add quantity -----
        final String fSize = size;
        final Integer fSugar = sugar;
        final String fIce = ice;
        final String fNote = note;

        var existing = cartItemRepos.findByCartId(cartId).stream()
                .filter(i -> i.getProduct() != null
                        && i.getProduct().getId().equals(productId)
                        && Objects.equals(i.getSize(), fSize)
                        && Objects.equals(i.getSugar(), fSugar)
                        && Objects.equals(i.getIce(), fIce)
                        && Objects.equals(i.getNote(), fNote))
                .findFirst();

        if (existing.isPresent()) {
            CartItems item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
            return ResponseEntity.ok(cartItemRepos.save(item));
        }

        CartItems item = new CartItems();
        item.setCart(cart.get());
        item.setProduct(product.get());
        item.setQuantity(quantity);
        item.setSize(size);
        item.setSugar(sugar);
        item.setIce(ice);
        item.setNote(note);

        return ResponseEntity.status(HttpStatus.CREATED).body(cartItemRepos.save(item));
    }

    // Increase quantity
    @PutMapping("/{id}/increase")
    public ResponseEntity<?> increase(@PathVariable("id") Integer id) {

        var item = cartItemRepos.findById(id);
        if (item.isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        CartItems cartItem = item.get();
        cartItem.setQuantity(cartItem.getQuantity() + 1);
        return ResponseEntity.ok(cartItemRepos.save(cartItem));
    }

    // Decrease quantity (removes the line when it reaches 0)
    @PutMapping("/{id}/decrease")
    public ResponseEntity<?> decrease(@PathVariable("id") Integer id) {

        var item = cartItemRepos.findById(id);
        if (item.isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        CartItems cartItem = item.get();

        if (cartItem.getQuantity() > 1) {
            cartItem.setQuantity(cartItem.getQuantity() - 1);
            return ResponseEntity.ok(cartItemRepos.save(cartItem));
        }

        cartItemRepos.delete(cartItem);
        return ResponseEntity.ok(Map.of("message", "Product removed from cart"));
    }

    // Remove an item completely
    @DeleteMapping("/{id}")
    public ResponseEntity<?> removeItem(@PathVariable("id") Integer id) {

        var item = cartItemRepos.findById(id);
        if (item.isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Cart item not found");
        }
        cartItemRepos.delete(item.get());
        return ResponseEntity.ok(Map.of("message", "Cart item removed successfully"));
    }

    // Empty all items from a cart
    @DeleteMapping("/cart/{cartId}/clear")
    public Object clearCart(@PathVariable("cartId") Integer cartId) {

        if (cartRepos.findById(cartId).isEmpty()) {
            return msg(HttpStatus.NOT_FOUND, "Cart not found");
        }
        var items = cartItemRepos.findByCartId(cartId);
        if (items.isEmpty()) {
            return msg(HttpStatus.BAD_REQUEST, "Cart is already empty");
        }
        cartItemRepos.deleteAll(items);
        return ResponseEntity.ok(Map.of("message", "All items cleared from cart " + cartId));
    }
}