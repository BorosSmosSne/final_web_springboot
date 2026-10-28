package com.setec.telegrambot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import com.setec.entities.Order;
import com.setec.entities.OrderItems;
import com.setec.entities.OrderStatus;
import com.setec.entities.Product;
import com.setec.repos.OrderRepos;
import com.setec.repos.ProductRepo;
import com.setec.service.OrderService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class MyTelegramBot {

	@Value("${token}")
	private String token;
	@Value("${chat_id}")
	private long chat_id;
	@Value("${usd_rate:4100}")      // optional property: Riel per 1 USD
	private double usdRate;

	private TelegramBot bot;

	private final OrderService orderService;
	private final OrderRepos orderRepos;
	private final ProductRepo productRepo;

	private static final String LINE = "━━━━━━━━━━━━━━";
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM · HH:mm", Locale.ENGLISH);

	public MyTelegramBot(OrderService orderService, OrderRepos orderRepos, ProductRepo productRepo) {
		this.orderService = orderService;
		this.orderRepos = orderRepos;
		this.productRepo = productRepo;
	}

	// =====================================================================
	// START / STOP
	// =====================================================================

	@PostConstruct
	public void init() {
		if (token == null || token.isBlank() || token.equals("dummy_token")) {
			System.err.println("Telegram token not set. Bot is disabled.");
			return;
		}
		bot = new TelegramBot(token);

		// Listen for button taps and commands
		bot.setUpdatesListener(updates -> {
			for (Update u : updates) {
				try {
					if (u.callbackQuery() != null) {
						handleCallback(u.callbackQuery());
					} else if (u.message() != null && u.message().text() != null) {
						handleCommand(u.message());
					}
				} catch (Exception e) {
					System.err.println("Telegram update error: " + e.getMessage());
				}
			}
			return UpdatesListener.CONFIRMED_UPDATES_ALL;
		});
	}

	// Important with Spring DevTools restarts: stop the old listener,
	// otherwise two listeners fight over the same bot.
	@PreDestroy
	public void shutdown() {
		if (bot != null) {
			bot.removeGetUpdatesListener();
			bot.shutdown();
		}
	}

	// =====================================================================
	// PUBLIC API (used by controllers)
	// =====================================================================

	/** Plain message (kept for backward compatibility). */
	public SendResponse message(String text) {
		if (bot == null || chat_id == 0) {
			System.err.println("Telegram not configured. Message not sent.");
			return null;
		}
		return bot.execute(new SendMessage(chat_id, text));
	}

	/** Sends the ticket with buttons and remembers its message id. Never throws. */
	public void sendNewOrderTicket(Order order) {
		if (bot == null || chat_id == 0) {
			System.err.println("Telegram not configured. Ticket for order #" + order.getId() + " not sent.");
			return;
		}
		try {
			SendResponse r = bot.execute(new SendMessage(chat_id, buildTicket(order))
					.parseMode(ParseMode.HTML)
					.replyMarkup(keyboardFor(order)));
			if (r != null && r.isOk() && r.message() != null) {
				orderService.setTelegramMessageId(order.getId(), r.message().messageId());
				System.out.println("Telegram ticket sent for order #" + order.getId());
			} else {
				System.err.println("Telegram ticket failed: " + (r == null ? "no response" : r.description()));
			}
		} catch (Exception e) {
			System.err.println("Could not send Telegram ticket: " + e.getMessage());
		}
	}

	/** Re-draws the existing ticket (new status text + new buttons). Never throws. */
	public void refreshTicket(Order order) {
		if (bot == null || chat_id == 0 || order.getTelegramMessageId() == null) {
			return;
		}
		try {
			BaseResponse r = bot.execute(new EditMessageText(chat_id, order.getTelegramMessageId(), buildTicket(order))
					.parseMode(ParseMode.HTML)
					.replyMarkup(keyboardFor(order)));
			if (!r.isOk()) {
				System.err.println("Telegram edit failed: " + r.description());
			}
		} catch (Exception e) {
			System.err.println("Could not edit Telegram ticket: " + e.getMessage());
		}
	}

	// =====================================================================
	// BUTTONS
	// =====================================================================

	private void handleCallback(CallbackQuery cq) {
		String data = cq.data();
		if (data == null || !data.startsWith("o:")) {
			answer(cq.id(), null);
			return;
		}
		try {
			String[] p = data.split(":");                     // o : 42 : PREPARING
			Integer orderId = Integer.valueOf(p[1]);
			OrderStatus next = OrderStatus.valueOf(p[2]);

			Order order = orderService.changeStatus(orderId, next);
			refreshTicket(order);
			answer(cq.id(), "Order #" + orderId + " → " + next);
		} catch (NoSuchElementException | IllegalStateException e) {
			answer(cq.id(), e.getMessage());
		} catch (Exception e) {
			answer(cq.id(), "Something went wrong");
		}
	}

	private InlineKeyboardButton btn(String label, Integer orderId, OrderStatus target) {
		return new InlineKeyboardButton(label).callbackData("o:" + orderId + ":" + target.name());
	}

	private InlineKeyboardMarkup keyboardFor(Order o) {
		InlineKeyboardMarkup kb = new InlineKeyboardMarkup();
		Integer id = o.getId();
		switch (o.getStatus()) {
			case NEW -> kb.addRow(
					btn("✅ Accept", id, OrderStatus.ACCEPTED),
					btn("❌ Reject", id, OrderStatus.CANCELLED));
			case ACCEPTED -> kb.addRow(
					btn("👨‍🍳 Start preparing", id, OrderStatus.PREPARING),
					btn("❌ Cancel", id, OrderStatus.CANCELLED));
			case PREPARING -> kb.addRow(
					btn("🛎 Ready", id, OrderStatus.READY),
					btn("❌ Cancel", id, OrderStatus.CANCELLED));
			case READY -> kb.addRow(
					btn("🏁 Completed", id, OrderStatus.COMPLETED));
			default -> { }   // COMPLETED / CANCELLED: no buttons
		}
		return kb;
	}

	private void answer(String callbackId, String text) {
		AnswerCallbackQuery a = new AnswerCallbackQuery(callbackId);
		if (text != null) {
			a.text(text);
		}
		bot.execute(a);
	}

	// =====================================================================
	// TICKET DESIGN
	// =====================================================================

	private String title(OrderStatus s) {
		return switch (s) {
			case NEW -> "🔔 NEW ORDER";
			case ACCEPTED -> "✅ ACCEPTED";
			case PREPARING -> "👨‍🍳 PREPARING";
			case READY -> "🛎 READY";
			case COMPLETED -> "🏁 COMPLETED";
			case CANCELLED -> "❌ CANCELLED";
		};
	}

	private String statusLabel(OrderStatus s) {
		return switch (s) {
			case NEW -> "🆕 NEW";
			case ACCEPTED -> "✅ ACCEPTED";
			case PREPARING -> "👨‍🍳 PREPARING";
			case READY -> "🛎 READY";
			case COMPLETED -> "🏁 COMPLETED";
			case CANCELLED -> "❌ CANCELLED";
		};
	}

	private String buildTicket(Order o) {
		StringBuilder sb = new StringBuilder();

		sb.append("<b>").append(title(o.getStatus())).append(" #").append(o.getId()).append("</b>\n");
		if (o.getCreatedAt() != null) {
			sb.append("🕒 ").append(TIME_FMT.format(o.getCreatedAt())).append("\n");
		}
		sb.append(LINE).append("\n");

		sb.append("👤 ").append(esc(o.getCustomerName()))
				.append("  ·  📞 ").append(esc(o.getPhone())).append("\n");

		String where = typeLabel(o);
		if (o.getAddress() != null && !o.getAddress().isBlank()) {
			where += " · " + esc(o.getAddress());
		}
		sb.append(where).append("\n");

		String pay = o.getPaymentMethod() == null ? "CASH" : o.getPaymentMethod();
		sb.append("💵 ").append(esc(pay)).append("\n");
		sb.append(LINE).append("\n");

		for (OrderItems it : o.getOrderItems()) {
			sb.append(emojiFor(it)).append(" ").append(it.getQuantity()).append(" × ")
					.append(esc(itemName(it)));
			if (it.getSize() != null) {
				sb.append(" (").append(esc(it.getSize())).append(")");
			}
			sb.append("\n");

			List<String> details = new ArrayList<>();
			if (it.getSugar() != null) {
				details.add(it.getSugar() + "% sugar");
			}
			if (it.getIce() != null) {
				details.add(iceLabel(it.getIce()));
			}
			if (it.getNote() != null && !it.getNote().isBlank()) {
				details.add("“" + esc(it.getNote()) + "”");
			}
			if (!details.isEmpty()) {
				sb.append("      └ ").append(String.join(" · ", details)).append("\n");
			}
		}

		if (o.getNote() != null && !o.getNote().isBlank()) {
			sb.append("📝 <i>").append(esc(o.getNote())).append("</i>\n");
		}
		sb.append(LINE).append("\n");

		double total = o.getTotal() == null ? 0 : o.getTotal();
		sb.append("💰 <b>TOTAL  ").append(riel(total)).append("</b>  (≈ ")
				.append(usd(total)).append(")\n");
		sb.append("Status: ").append(statusLabel(o.getStatus()));
		return sb.toString();
	}

	private String typeLabel(Order o) {
		if (o.getOrderType() == null) {
			return "🥡 Takeaway";
		}
		return switch (o.getOrderType()) {
			case DINE_IN -> "🍽 Dine-in";
			case TAKEAWAY -> "🥡 Takeaway";
			case DELIVERY -> "🛵 Delivery";
		};
	}

	private String iceLabel(String ice) {
		return switch (ice.toUpperCase()) {
			case "NONE" -> "no ice";
			case "LESS" -> "less ice";
			case "NORMAL" -> "normal ice";
			default -> esc(ice.toLowerCase()) + " ice";
		};
	}

	private String emojiFor(OrderItems it) {
		String cat = it.getProduct() == null || it.getProduct().getCategoryName() == null
				? "" : it.getProduct().getCategoryName().toLowerCase();
		if (cat.contains("pastr") || cat.contains("bakery")) return "🥐";
		if (cat.contains("frappe")) return "🥤";
		if (cat.contains("iced")) return "🧊";
		if (cat.contains("tea")) return "🍵";
		return "☕";
	}

	private String itemName(OrderItems it) {
		if (it.getProductName() != null) return it.getProductName();
		return it.getProduct() != null ? it.getProduct().getName() : "?";
	}

	private String riel(double v) {
		return String.format(Locale.US, "%,d ៛", Math.round(v));
	}

	private String usd(double riel) {
		return String.format(Locale.US, "$%.2f", usdRate > 0 ? riel / usdRate : 0);
	}

	// Telegram HTML mode: customer text must be escaped
	private String esc(String s) {
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	// =====================================================================
	// COMMANDS
	// =====================================================================

	private void handleCommand(Message msg) {
		// Only the configured staff chat may use commands
		if (msg.chat() == null || msg.chat().id() == null || msg.chat().id() != chat_id) {
			return;
		}
		String text = msg.text().trim();
		if (!text.startsWith("/")) {
			return;
		}
		String[] parts = text.split("\\s+", 2);
		String cmd = parts[0].split("@")[0].toLowerCase();    // /pending@MyBot -> /pending
		String arg = parts.length > 1 ? parts[1].trim() : "";

		switch (cmd) {
			case "/start", "/help" -> reply(helpText());
			case "/pending" -> reply(pendingText());
			case "/today" -> reply(todayText());
			case "/menu" -> reply(menuText());
			case "/soldout" -> reply(setAvailability(arg, false));
			case "/soldin" -> reply(setAvailability(arg, true));
			default -> reply("Unknown command. Send /help");
		}
	}

	private void reply(String html) {
		bot.execute(new SendMessage(chat_id, html).parseMode(ParseMode.HTML));
	}

	private String helpText() {
		return "☕ <b>Coffee Shop Bot</b>\n\n"
				+ "/pending – orders still in progress\n"
				+ "/today – today’s orders and revenue\n"
				+ "/menu – menu with availability\n"
				+ "/soldout Iced Latte – mark as sold out\n"
				+ "/soldin Iced Latte – available again\n\n"
				+ "New orders arrive here with buttons.";
	}

	private String pendingText() {
		List<Order> list = orderRepos.findByStatusInOrderByIdAsc(
				List.of(OrderStatus.NEW, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY));
		if (list.isEmpty()) {
			return "🎉 No pending orders.";
		}
		StringBuilder sb = new StringBuilder("📋 <b>Pending orders (" + list.size() + ")</b>\n\n");
		for (Order o : list) {
			sb.append("#").append(o.getId()).append(" · ").append(esc(o.getCustomerName()))
					.append(" · ").append(riel(o.getTotal() == null ? 0 : o.getTotal()))
					.append(" · ").append(statusLabel(o.getStatus())).append("\n");
		}
		return sb.toString();
	}

	private String todayText() {
		LocalDate d = LocalDate.now();
		List<Order> orders = orderRepos.findByCreatedAtBetween(d.atStartOfDay(), d.plusDays(1).atStartOfDay());

		int count = 0;
		int cancelled = 0;
		double revenue = 0;
		Map<String, Integer> qty = new HashMap<>();

		for (Order o : orders) {
			if (o.getStatus() == OrderStatus.CANCELLED) {
				cancelled++;
				continue;
			}
			count++;
			revenue += o.getTotal() == null ? 0 : o.getTotal();
			for (OrderItems it : o.getOrderItems()) {
				qty.merge(itemName(it), it.getQuantity() == null ? 0 : it.getQuantity(), Integer::sum);
			}
		}

		StringBuilder sb = new StringBuilder("📊 <b>Today</b>\n\n");
		sb.append("Orders: ").append(count).append("\n");
		sb.append("Cancelled: ").append(cancelled).append("\n");
		sb.append("Revenue: <b>").append(riel(revenue)).append("</b> (≈ ").append(usd(revenue)).append(")\n");

		List<Map.Entry<String, Integer>> top = new ArrayList<>(qty.entrySet());
		top.sort((a, b) -> b.getValue() - a.getValue());
		if (!top.isEmpty()) {
			sb.append("\n🏆 <b>Top items</b>\n");
			for (int i = 0; i < Math.min(3, top.size()); i++) {
				sb.append(i + 1).append(". ").append(esc(top.get(i).getKey()))
						.append(" × ").append(top.get(i).getValue()).append("\n");
			}
		}
		return sb.toString();
	}

	private String menuText() {
		Map<String, List<Product>> byCat = new LinkedHashMap<>();
		for (Product p : productRepo.findAll()) {
			String cat = p.getCategoryName() == null ? "Other" : p.getCategoryName();
			byCat.computeIfAbsent(cat, k -> new ArrayList<>()).add(p);
		}
		if (byCat.isEmpty()) {
			return "The menu is empty.";
		}
		StringBuilder sb = new StringBuilder("📖 <b>Menu</b>\n");
		for (Map.Entry<String, List<Product>> e : byCat.entrySet()) {
			sb.append("\n<b>").append(esc(e.getKey())).append("</b>\n");
			for (Product p : e.getValue()) {
				sb.append(p.getAvailable() ? "✅ " : "⛔ ").append(esc(p.getName()))
						.append(" — ").append(riel(p.getPrice())).append("\n");
			}
		}
		return sb.toString();
	}

	private String setAvailability(String name, boolean available) {
		if (name.isBlank()) {
			return "Usage: /" + (available ? "soldin" : "soldout") + " Iced Latte";
		}
		List<Product> found = productRepo.findByNameContainingIgnoreCase(name);
		if (found.isEmpty()) {
			return "❓ No product matches “" + esc(name) + "”";
		}
		Product target = found.stream().filter(p -> p.getName().equalsIgnoreCase(name)).findFirst()
				.orElse(found.size() == 1 ? found.get(0) : null);
		if (target == null) {
			StringBuilder sb = new StringBuilder("Several products match, be more specific:\n");
			for (Product p : found) {
				sb.append("• ").append(esc(p.getName())).append("\n");
			}
			return sb.toString();
		}
		target.setAvailable(available);
		productRepo.save(target);
		return available
				? "✅ " + esc(target.getName()) + " is available again"
				: "⛔ " + esc(target.getName()) + " marked SOLD OUT";
	}
}