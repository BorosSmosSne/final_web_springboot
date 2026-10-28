package com.setec.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.setec.entities.CartItems;
import com.setec.entities.Order;
import com.setec.entities.OrderItems;
import com.setec.entities.OrderStatus;
import com.setec.entities.OrderType;
import com.setec.repos.CartItemRepos;
import com.setec.repos.CartRepos;
import com.setec.repos.OrderItemRepos;
import com.setec.repos.OrderRepos;

@Service
public class OrderService {

    // Allowed status changes
    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            OrderStatus.NEW,       EnumSet.of(OrderStatus.ACCEPTED, OrderStatus.CANCELLED),
            OrderStatus.ACCEPTED,  EnumSet.of(OrderStatus.PREPARING, OrderStatus.CANCELLED),
            OrderStatus.PREPARING, EnumSet.of(OrderStatus.READY, OrderStatus.CANCELLED),
            OrderStatus.READY,     EnumSet.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    private final CartRepos cartRepos;
    private final CartItemRepos cartItemRepos;
    private final OrderRepos orderRepos;
    private final OrderItemRepos orderItemRepos;

    public OrderService(CartRepos cartRepos, CartItemRepos cartItemRepos,
                        OrderRepos orderRepos, OrderItemRepos orderItemRepos) {
        this.cartRepos = cartRepos;
        this.cartItemRepos = cartItemRepos;
        this.orderRepos = orderRepos;
        this.orderItemRepos = orderItemRepos;
    }

    /**
     * Turns a cart into an order and empties the cart.
     * NoSuchElementException     -> cart not found      (404)
     * IllegalArgumentException   -> bad input            (400)
     * IllegalStateException      -> product sold out     (409)
     */
    @Transactional
    public Order placeOrder(Integer cartId, String customerName, String phone, String address,
                            OrderType orderType, String paymentMethod, String note) {

        if (!cartRepos.existsById(cartId)) {
            throw new NoSuchElementException("Cart not found");
        }
        if (customerName == null || customerName.isBlank()) {
            throw new IllegalArgumentException("customerName is required");
        }
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("phone is required");
        }

        List<CartItems> cartItems = cartItemRepos.findByCartId(cartId);
        if (cartItems.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        for (CartItems ci : cartItems) {
            if (ci.getProduct() == null || !ci.getProduct().getAvailable()) {
                String name = ci.getProduct() == null ? "A product" : ci.getProduct().getName();
                throw new IllegalStateException(name + " is sold out. Remove it from the cart and try again.");
            }
        }

        boolean hasAddress = address != null && !address.isBlank();
        OrderType type = orderType != null ? orderType
                : (hasAddress ? OrderType.DELIVERY : OrderType.TAKEAWAY);
        if (type == OrderType.DELIVERY && !hasAddress) {
            throw new IllegalArgumentException("address is required for DELIVERY");
        }

        Order order = new Order();
        order.setCustomerName(customerName.trim());
        order.setPhone(phone.trim());
        order.setAddress(hasAddress ? address.trim() : null);
        order.setOrderType(type);
        order.setPaymentMethod(paymentMethod == null || paymentMethod.isBlank()
                ? "CASH" : paymentMethod.trim().toUpperCase());
        order.setNote(note == null || note.isBlank() ? null : note.trim());
        order.setStatus(OrderStatus.NEW);
        order.setCreatedAt(LocalDateTime.now());

        double total = 0;
        for (CartItems ci : cartItems) {
            total += ci.getProduct().getPrice() * ci.getQuantity();
        }
        order.setTotal(total);

        Order saved = orderRepos.save(order);

        List<OrderItems> savedItems = new ArrayList<>();
        for (CartItems ci : cartItems) {
            OrderItems oi = new OrderItems();
            oi.setOrder(saved);
            oi.setProduct(ci.getProduct());
            oi.setProductName(ci.getProduct().getName());   // snapshot
            oi.setQuantity(ci.getQuantity());
            oi.setPrice(ci.getProduct().getPrice());        // price at order time
            oi.setSize(ci.getSize());
            oi.setSugar(ci.getSugar());
            oi.setIce(ci.getIce());
            oi.setNote(ci.getNote());
            savedItems.add(orderItemRepos.save(oi));
        }
        saved.setOrderItems(savedItems);

        cartItemRepos.deleteAll(cartItems);   // empty the cart
        return saved;
    }

    /**
     * Single place for status changes: used by PATCH /api/orders/{id}/status
     * and by the Telegram buttons, so they always follow the same rules.
     */
    @Transactional
    public Order changeStatus(Integer id, OrderStatus next) {
        Order order = orderRepos.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Order #" + id + " not found"));

        OrderStatus current = order.getStatus();
        if (current == next) {
            throw new IllegalStateException("Order #" + id + " is already " + current);
        }
        if (!NEXT.getOrDefault(current, Set.of()).contains(next)) {
            throw new IllegalStateException("Order #" + id + " is " + current + ", it cannot become " + next);
        }
        order.setStatus(next);
        return orderRepos.save(order);
    }

    @Transactional
    public void setTelegramMessageId(Integer orderId, Integer messageId) {
        orderRepos.findById(orderId).ifPresent(o -> {
            o.setTelegramMessageId(messageId);
            orderRepos.save(o);
        });
    }
}