package com.setec.telegrambot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;

import jakarta.annotation.PostConstruct;
import lombok.Data;

@Service
@Data
public class MyTelegramBot {

	@Value("${token}")
	private String token;
	@Value("${chat_id}")
	private long chat_id;
	
	private TelegramBot bot;
//	public SendResponse message(String text) {
//		if(bot==null) {
//			bot =new TelegramBot(token);
//		}
//		SendResponse sendMessage =bot.execute(new SendMessage(chat_id, text));
//		return sendMessage;
//	}
	@PostConstruct
	public void init() {
		if (token != null && !token.isBlank() && !token.equals("dummy_token")) {
			this.bot = new TelegramBot(token);
		}
	}

	public SendResponse message(String text) {
		if (bot == null) {
			bot = new TelegramBot(token);
		}
		if (chat_id == 0) {
			System.err.println("Telegram chat_id is not set. Message not sent.");
			return null;
		}
		return bot.execute(new SendMessage(chat_id, text));
	}
}
