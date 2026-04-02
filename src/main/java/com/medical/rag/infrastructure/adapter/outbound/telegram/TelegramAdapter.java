package com.medical.rag.infrastructure.adapter.outbound.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class TelegramAdapter {

    private final RestTemplate restTemplate;
    private final String botToken;
    private static final String URL = "https://api.telegram.org/bot{token}/sendMessage";

    public TelegramAdapter(RestTemplate restTemplate, @Value("${telegram.bot-token:}") String botToken) {
        this.restTemplate = restTemplate;
        this.botToken = botToken;
    }

    public void sendMessage(String chatId, String text) {
        if (text.length() > 4000) {
            int mid = text.lastIndexOf("\n", 4000);
            if (mid == -1) mid = 4000;
            sendMessage(chatId, text.substring(0, mid));
            sendMessage(chatId, text.substring(mid));
            return;
        }
        try {
            restTemplate.postForObject(URL, Map.of(
                    "chat_id", chatId,
                    "text", text
            ), String.class, botToken);
        } catch (Exception e) {
            // fallback sem formatação
            restTemplate.postForObject(URL, Map.of(
                    "chat_id", chatId,
                    "text", text.replaceAll("[*_`\\[\\]]", "")
            ), String.class, botToken);
        }
    }
}
