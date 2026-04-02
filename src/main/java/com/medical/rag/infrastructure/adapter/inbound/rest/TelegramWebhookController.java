package com.medical.rag.infrastructure.adapter.inbound.rest;

import com.medical.rag.domain.model.MedicalResponse;
import com.medical.rag.domain.port.QueryPort;
import com.medical.rag.infrastructure.adapter.outbound.telegram.TelegramAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/telegram")
public class TelegramWebhookController {

    private static final Logger log = LoggerFactory.getLogger(TelegramWebhookController.class);

    private final QueryPort queryPort;
    private final TelegramAdapter telegram;

    public TelegramWebhookController(QueryPort queryPort, TelegramAdapter telegram) {
        this.queryPort = queryPort;
        this.telegram = telegram;
    }

    @PostMapping("/webhook")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Void> handleUpdate(@RequestBody Map<String, Object> update) {
        Map<String, Object> message = (Map<String, Object>) update.get("message");
        if (message == null) return ResponseEntity.ok().build();

        Map<String, Object> chat = (Map<String, Object>) message.get("chat");
        String chatId = String.valueOf(((Number) chat.get("id")).longValue());
        String text = (String) message.get("text");

        if (text == null) return ResponseEntity.ok().build();

        log.info("Telegram [{}]: {}", chatId, text);

        if (text.equals("/start")) {
            telegram.sendMessage(chatId,
                    "🏥 Bem-vindo ao Medical RAG!\n\n" +
                    "Sou um assistente médico baseado em documentos oficiais do Ministério da Saúde (PCDTs, CONITEC, RENAME).\n\n" +
                    "Envie sua pergunta médica e responderei com base nos protocolos clínicos.\n\n" +
                    "Exemplos:\n" +
                    "• Qual o tratamento para diabetes tipo 2 no SUS?\n" +
                    "• Posso prescrever ibuprofeno com varfarina?\n" +
                    "• Qual o protocolo de hipertensão?\n\n" +
                    "Comandos:\n" +
                    "/start - Menu inicial\n" +
                    "/ajuda - Como usar");
        } else if (text.equals("/ajuda")) {
            telegram.sendMessage(chatId,
                    "📖 Como usar:\n\n" +
                    "Basta enviar sua pergunta médica em linguagem natural.\n\n" +
                    "O sistema busca nos documentos oficiais do SUS e gera uma resposta com as fontes.\n\n" +
                    "⚠️ As respostas são apenas referência e não substituem avaliação médica profissional.");
        } else {
            telegram.sendMessage(chatId, "🔍 Buscando nos protocolos clínicos...");

            try {
                MedicalResponse response = queryPort.query(text, null);

                StringBuilder sb = new StringBuilder();
                sb.append(response.answer());

                if (!response.sources().isEmpty()) {
                    sb.append("\n\n📚 Fontes:\n");
                    response.sources().forEach(s -> sb.append("• ").append(s).append("\n"));
                }

                sb.append("\n").append(response.disclaimer());

                telegram.sendMessage(chatId, sb.toString());
            } catch (Exception e) {
                log.error("Erro ao processar pergunta do Telegram: {}", e.getMessage());
                telegram.sendMessage(chatId, "❌ Erro ao processar sua pergunta. Tente novamente em alguns instantes.");
            }
        }

        return ResponseEntity.ok().build();
    }
}
