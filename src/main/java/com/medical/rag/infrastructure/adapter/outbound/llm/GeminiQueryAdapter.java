package com.medical.rag.infrastructure.adapter.outbound.llm;

import com.medical.rag.domain.model.MedicalResponse;
import com.medical.rag.domain.port.QueryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.slf4j.MDC;

@Component
public class GeminiQueryAdapter implements QueryPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiQueryAdapter.class);
    private static final int MAX_HISTORY = 10;

    private static final String SYSTEM_PROMPT = """
            Você é um assistente médico especializado. Siga estas regras:
            1. Responda APENAS com base no contexto fornecido e no histórico da conversa.
            2. Se não encontrar informação suficiente, diga claramente.
            3. Sempre cite as fontes (nome do documento/bula/protocolo).
            4. NUNCA faça diagnósticos — forneça apenas informações de referência.
            5. Responda em português brasileiro.
            6. Use formatação clara com tópicos quando apropriado.
            7. Considere o histórico da conversa para entender referências a perguntas anteriores.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final Map<String, List<Message>> chatHistory = new ConcurrentHashMap<>();

    public GeminiQueryAdapter(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.vectorStore = vectorStore;
    }

    @Override
    public MedicalResponse query(String question, String specialty) {
        return query(question, specialty, null);
    }

    @Override
    public MedicalResponse query(String question, String specialty, String sessionId) {
        long startTime = System.currentTimeMillis();

        try {
            MDC.put("sessionId", sessionId != null ? sessionId : "anonymous");
            MDC.put("specialty", specialty != null ? specialty : "geral");

            // 1. Busca vetorial
            long searchStart = System.currentTimeMillis();
            List<Document> docs = searchDocuments(question, specialty);
            long searchMs = System.currentTimeMillis() - searchStart;

            log.info("[SEARCH] question=\"{}\" docs_found={} search_ms={}", question, docs.size(), searchMs);

            if (docs.isEmpty()) {
                log.warn("[GAP] Nenhum documento encontrado para: \"{}\"", question);
                return new MedicalResponse(
                        "Não encontrei informações relevantes na base de dados para essa pergunta.",
                        List.of()
                );
            }

            String context = docs.stream()
                    .map(d -> "[%s - %s] %s".formatted(
                            d.getMetadata().getOrDefault("source", "N/A"),
                            d.getMetadata().getOrDefault("fileName", "N/A"),
                            d.getContent()))
                    .collect(Collectors.joining("\n\n"));

            String userPrompt = """
                    CONTEXTO (documentos médicos):
                    %s
                    
                    PERGUNTA DO MÉDICO: %s
                    """.formatted(context, question);

            // 2. Chamada ao Gemini
            List<Message> history = (sessionId != null) ? chatHistory.getOrDefault(sessionId, List.of()) : List.of();

            long llmStart = System.currentTimeMillis();
            String answer = chatClient.prompt()
                    .messages(history)
                    .user(userPrompt)
                    .call()
                    .content();
            long llmMs = System.currentTimeMillis() - llmStart;

            // 3. Salva no histórico
            if (sessionId != null) {
                List<Message> updated = new ArrayList<>(history);
                updated.add(new UserMessage(userPrompt));
                updated.add(new AssistantMessage(answer));
                if (updated.size() > MAX_HISTORY * 2) {
                    updated = new ArrayList<>(updated.subList(updated.size() - MAX_HISTORY * 2, updated.size()));
                }
                chatHistory.put(sessionId, updated);
            }

            List<String> sources = docs.stream()
                    .map(d -> "%s (%s)".formatted(
                            d.getMetadata().getOrDefault("fileName", "N/A"),
                            d.getMetadata().getOrDefault("source", "N/A")))
                    .distinct()
                    .toList();

            long totalMs = System.currentTimeMillis() - startTime;

            log.info("[QUERY] question=\"{}\" docs_found={} sources={} answer_length={} search_ms={} llm_ms={} total_ms={}",
                    question, docs.size(), sources, answer.length(), searchMs, llmMs, totalMs);

            return new MedicalResponse(answer, sources);
        } catch (Exception e) {
            long totalMs = System.currentTimeMillis() - startTime;
            log.error("[ERROR] question=\"{}\" error=\"{}\" total_ms={}", question, e.getMessage(), totalMs, e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    private List<Document> searchDocuments(String question, String specialty) {
        var searchBuilder = SearchRequest.query(question).withTopK(5).withSimilarityThreshold(0.3);

        if (specialty != null && !specialty.isBlank()) {
            var filter = new FilterExpressionBuilder()
                    .eq("specialty", specialty)
                    .build();
            searchBuilder = searchBuilder.withFilterExpression(filter);
        }

        return vectorStore.similaritySearch(searchBuilder);
    }

    @Override
    public void clearSession(String sessionId) {
        chatHistory.remove(sessionId);
        log.info("[SESSION_CLEAR] sessionId={}", sessionId);
    }
}
