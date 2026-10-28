package com.setec.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.setec.entities.Order;
import com.setec.entities.OrderStatus;
import com.setec.entities.OrderType;
import com.setec.repos.OrderRepos;
import com.setec.service.OrderService;
import com.setec.telegrambot.MyTelegramBot;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

// Old paths (/api/cart/checkout, /api/cart/orders) still work.
// New paths (/api/orders/...) are the recommended ones.
@RestController
@RequestMapping("/api")
public class OrderController {

	private final OrderService orderService;
	private final OrderRepos orderRepos;
	private final MyTelegramBot myTelegramBot;

	public OrderController(OrderService orderService, OrderRepos orderRepos, MyTelegramBot myTelegramBot) {
		this.orderService = orderService;
		this.orderRepos = orderRepos;
		this.myTelegramBot = myTelegramBot;
	}

	private static ResponseEntity<?> msg(HttpStatus status, String text) {
		return ResponseEntity.status(status).body(Map.of("message", text));
	}

	private static String str(Map<String, Object> map, String key) {
		Object v = map.get(key);
		return v == null ? null : v.toString().trim();
	}

	// ------------------------------------------------------------------
	// CHECKOUT
	// ------------------------------------------------------------------
	@PostMapping({ "/cart/checkout", "/orders/checkout" })
	@io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Checkout Example", value = """
			{
			  "cartId": 1,
			  "customerName": "Dara",
			  "phone": "012345678",
			  "orderType": "DELIVERY",
			  "paymentMethod": "KHQR",
			  "address": "St. 51, Phnom Penh",
			  "note": "Call when you arrive"
			}
			""")))
	public Object checkout(@RequestBody Map<String, Object> request) {

		Object cartIdValue = request.get("cartId");
		if (cartIdValue == null) {
			return msg(HttpStatus.BAD_REQUEST, "cartId is required");
		}
		Integer cartId;
		try {
			cartId = Integer.valueOf(cartIdValue.toString());
		} catch (NumberFormatException e) {
			return msg(HttpStatus.BAD_REQUEST, "cartId must be a number");
		}

		OrderType type = null;
		String typeText = str(request, "orderType");
		if (typeText != null && !typeText.isBlank()) {
			try {
				type = OrderType.valueOf(typeText.toUpperCase());
			} catch (IllegalArgumentException e) {
				return msg(HttpStatus.BAD_REQUEST, "orderType must be DINE_IN, TAKEAWAY or DELIVERY");
			}
		}

		try {
			Order order = orderService.placeOrder(cartId,
					str(request, "customerName"), str(request, "phone"), str(request, "address"),
					type, str(request, "paymentMethod"), str(request, "note"));

			myTelegramBot.sendNewOrderTicket(order);   // never throws
			return ResponseEntity.ok(order);

		} catch (NoSuchElementException e) {
			return msg(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (IllegalArgumentException e) {
			return msg(HttpStatus.BAD_REQUEST, e.getMessage());
		} catch (IllegalStateException e) {
			return msg(HttpStatus.CONFLICT, e.getMessage());
		}
	}

	// ------------------------------------------------------------------
	// READ
	// ------------------------------------------------------------------

	// GET /api/orders            all orders (newest first)
	// GET /api/orders?status=NEW only that status
	@GetMapping({ "/cart/orders", "/orders" })
	public Object getAllOrders(@RequestParam(name = "status", required = false) String status) {
		if (status == null || status.isBlank()) {
			return orderRepos.findAll(Sort.by(Sort.Direction.DESC, "id"));
		}
		try {
			return orderRepos.findByStatusOrderByIdDesc(OrderStatus.valueOf(status.trim().toUpperCase()));
		} catch (IllegalArgumentException e) {
			return msg(HttpStatus.BAD_REQUEST,
					"status must be NEW, ACCEPTED, PREPARING, READY, COMPLETED or CANCELLED");
		}
	}

	@GetMapping({ "/cart/orders/{id}", "/orders/{id}" })
	public Object getOrderById(@PathVariable("id") Integer id) {
		var order = orderRepos.findById(id);
		if (order.isPresent()) {
			return order.get();
		}
		return msg(HttpStatus.NOT_FOUND, "Order ID " + id + " not found");
	}

	// customer tracking: GET /api/orders/phone/012345678
	@GetMapping("/orders/phone/{phone}")
	public Object getOrdersByPhone(@PathVariable("phone") String phone) {
		return orderRepos.findByPhoneOrderByIdDesc(phone);
	}

	// ------------------------------------------------------------------
	// STATUS: PATCH /api/orders/{id}/status   body {"status":"PREPARING"}
	// ------------------------------------------------------------------
	@PatchMapping("/orders/{id}/status")
	public Object updateStatus(@PathVariable("id") Integer id, @RequestBody Map<String, String> body) {
		String s = body.get("status");
		if (s == null || s.isBlank()) {
			return msg(HttpStatus.BAD_REQUEST, "status is required");
		}
		OrderStatus next;
		try {
			next = OrderStatus.valueOf(s.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return msg(HttpStatus.BAD_REQUEST,
					"status must be NEW, ACCEPTED, PREPARING, READY, COMPLETED or CANCELLED");
		}
		try {
			Order order = orderService.changeStatus(id, next);
			myTelegramBot.refreshTicket(order);       // the Telegram ticket updates too
			return order;
		} catch (NoSuchElementException e) {
			return msg(HttpStatus.NOT_FOUND, e.getMessage());
		} catch (IllegalStateException e) {
			return msg(HttpStatus.CONFLICT, e.getMessage());
		}
	}
}