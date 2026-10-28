package com.setec.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.setec.entities.CartItems;
import com.setec.entities.Order;
import com.setec.entities.OrderItems;
import com.setec.repos.CartItemRepos;
import com.setec.repos.CartRepos;
import com.setec.repos.OrderItemRepos;
import com.setec.repos.OrderRepos;
import com.setec.repos.ProductRepo;
import com.setec.telegrambot.MyTelegramBot;

import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

@RestController
@RequestMapping("/api/cart")
public class OrderController {

	private final CartRepos cartRepos;
	private final CartItemRepos cartItemRepos;
	private final ProductRepo productRepo;
	private final OrderRepos orderRepos;
	private final OrderItemRepos orderItemRepos;
	private final MyTelegramBot myTelegramBot;

	public OrderController(
	        CartRepos cartRepos,
	        CartItemRepos cartItemRepos,
	        ProductRepo productRepo,
	        OrderRepos orderRepos,
	        OrderItemRepos orderItemRepos,
	        MyTelegramBot myTelegramBot) {

	    this.cartRepos = cartRepos;
	    this.cartItemRepos = cartItemRepos;
	    this.productRepo = productRepo;
	    this.orderRepos = orderRepos;
	    this.orderItemRepos = orderItemRepos;
	    this.myTelegramBot = myTelegramBot;
	}

	@PostMapping("/checkout")
	@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Checkout Example", value = """
			{
			  "cartId": 1,
			  "customerName": "Dara",
			  "phone": "012345678",
			  "address": "Phnom Penh"
			}
			""")))
	public Object checkout(
	        @RequestBody Map<String, Object> request) {

	    // 1. Get customer information from the request
	    Object cartIdValue = request.get("cartId");

	    if (cartIdValue == null) {
	        return ResponseEntity.badRequest()
	                .body(Map.of("message", "cartId is required"));
	    }

	    Integer cartId = Integer.valueOf(cartIdValue.toString());

	    String customerName = (String) request.get("customerName");
	    String phone = (String) request.get("phone");
	    String address = (String) request.get("address");

	    // 2. Find the cart
	    var cart = cartRepos.findById(cartId);

	    if (cart.isEmpty()) {
	        return ResponseEntity.status(HttpStatus.NOT_FOUND)
	                .body(Map.of("message", "Cart not found"));
	    }

	    // 3. Find the items belonging to this cart
	    var cartItems = cartItemRepos.findAll().stream()
	            .filter(item -> item.getCart().getId().equals(cartId))
	            .toList();

	    if (cartItems.isEmpty()) {
	        return ResponseEntity.badRequest()
	                .body(Map.of("message", "Cart is empty"));
	    }

	    // 4. Create the order
	    Order order = new Order();
	    order.setCustomerName(customerName);
	    order.setPhone(phone);
	    order.setAddress(address);

	    // 5. Calculate the total
	    double total = 0;

	    for (CartItems item : cartItems) {
	        total += item.getProduct().getPrice()
	                * item.getQuantity();
	    }

	    order.setTotal(total);

	    // 6. Save the order first
	    Order savedOrder = orderRepos.save(order);

	    // 7. Save each item belonging to the order
	    List<OrderItems> savedItems = new ArrayList<>();

	    for (CartItems item : cartItems) {

	        OrderItems orderItem = new OrderItems();

	        orderItem.setOrder(savedOrder);
	        orderItem.setProduct(item.getProduct());
	        orderItem.setQuantity(item.getQuantity());
	        orderItem.setPrice(item.getProduct().getPrice());

	        OrderItems savedItem = orderItemRepos.save(orderItem);

	        savedItems.add(savedItem);
	    }

	    savedOrder.setOrderItems(savedItems);

	    // 9. Return the saved order

	    // Build the Telegram notification
	    StringBuilder message = new StringBuilder();

	    message.append("NEW ORDER\n\n");
	    message.append("Order ID: ").append(savedOrder.getId()).append("\n");
	    message.append("Customer: ").append(customerName).append("\n");
	    message.append("Phone: ").append(phone).append("\n");
	    message.append("Address: ").append(address).append("\n\n");

	    message.append("ORDER ITEMS\n");

	    for (CartItems item : cartItems) {

	        String productName = item.getProduct().getName();
	        int quantity = item.getQuantity();
	        double price = item.getProduct().getPrice();
	        double subtotal = price * quantity;

	        message.append("- ").append(productName)
	                .append(" x ").append(quantity)
	                .append(" | Price: ").append(price)
	                .append(" | Subtotal: ").append(subtotal)
	                .append("\n");
	    }

	    message.append("\nTotal: ").append(total).append(" Riel");

	    // Send the notification to Telegram
	    try {
	        var response = myTelegramBot.message(message.toString());

	        if (response.isOk()) {
	            System.out.println("Telegram notification sent successfully");
	        } else {
	            System.err.println("Telegram notification failed");
	        }

	    } catch (Exception e) {
	        System.err.println("Could not send Telegram notification: "
	                + e.getMessage());
	    }

	    // Clear the cart after saving the order
	    cartItemRepos.deleteAll(cartItems);

	    return ResponseEntity.ok(savedOrder);

	}
	// Added: Get all orders (GET /api/cart/orders)
    @GetMapping("/orders")
    public Object getAllOrders() {
        var orders = orderRepos.findAll();
        if (orders.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No orders found"));
        }
        return orders;
    }

    // Added: Get an order by ID (GET /api/cart/orders/{id})
    @GetMapping("/orders/{id}")
    public Object getOrderById(@PathVariable("id") Integer id) {
        var order = orderRepos.findById(id);
        if (order.isPresent()) {
            return order.get();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Order ID " + id + " not found"));
    }

    // Added: Get item breakdown for an order (GET /api/cart/orders/{id}/items)
//    @GetMapping("/orders/{id}/items")
//    public Object getOrderItems(@PathVariable("id") Integer id) {
//        var items = orderItemRepos.findAll().stream()
//                .filter(item -> item.getOrder() != null && item.getOrder().getId().equals(id))
//                .toList();
//
//        if (items.isEmpty()) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND)
//                    .body(Map.of("message", "No items found for Order ID " + id));
//        }
//        return items;
//    }

}
