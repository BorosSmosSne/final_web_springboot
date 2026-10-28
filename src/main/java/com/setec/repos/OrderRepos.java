package com.setec.repos;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.setec.entities.Order;
import com.setec.entities.OrderStatus;

public interface OrderRepos extends JpaRepository<Order, Integer> {

    // Load the items AND each item's product AND the product's category in one go.
    // The Telegram thread has no open database session, so everything the ticket
    // needs must already be loaded when the method returns.

    @Override
    @EntityGraph(attributePaths = { "orderItems", "orderItems.product", "orderItems.product.category" })
    Optional<Order> findById(Integer id);

    @EntityGraph(attributePaths = { "orderItems", "orderItems.product", "orderItems.product.category" })
    List<Order> findByStatusInOrderByIdAsc(Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = { "orderItems", "orderItems.product", "orderItems.product.category" })
    List<Order> findByStatusOrderByIdDesc(OrderStatus status);

    @EntityGraph(attributePaths = { "orderItems", "orderItems.product", "orderItems.product.category" })
    List<Order> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = { "orderItems", "orderItems.product", "orderItems.product.category" })
    List<Order> findByPhoneOrderByIdDesc(String phone);
}