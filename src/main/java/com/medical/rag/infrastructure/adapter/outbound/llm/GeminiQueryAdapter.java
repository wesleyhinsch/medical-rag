package com.medical.rag.infrastructure.adapter.outbound.llm;

import com.medical.rag.domain.model.MedicalResponse;
import com.medical.rag.domain.port.QueryPort;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GeminiQueryAdapter implements QueryPort {

    private static final String SYSTEM_PROMPT = """
            Você é um assistente médico especializado. Siga estas regras:
            1. Responda APENAS com base no contexto fornecido.
            2. Se não encontrar informação suficiente, diga claramente.
            3. Sempre cite as fontes (nome do documento/bula/protocolo).
            4. NUNCA faça diagnósticos — forneça apenas informações de referência.
            5. Responda em português brasileiro.
            6. Use formatação clara com tópicos quando apropriado.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public GeminiQueryAdapter(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.vectorStore = vectorStore;
    }

    @Override
    public MedicalResponse query(String question, String specialty) {
        // 1. Busca chunks relevantes no pgvector
        var searchBuilder = SearchRequest.query(question).withTopK(5).withSimilarityThreshold(0.3);

        if (specialty != null && !specialty.isBlank()) {
            var filter = new FilterExpressionBuilder()
                    .eq("specialty", specialty)
                    .build();
            searchBuilder = searchBuilder.withFilterExpression(filter);
        }

        List<Document> docs = vectorStore.similaritySearch(searchBuilder);

        if (docs.isEmpty()) {
            return new MedicalResponse(
                    "Não encontrei informações relevantes na base de dados para essa pergunta.",
                    List.of()
            );
        }

        // 2. Monta contexto com fontes
        String context = docs.stream()
                .map(d -> "[%s - %s] %s".formatted(
                        d.getMetadata().getOrDefault("source", "N/A"),
                        d.getMetadata().getOrDefault("fileName", "N/A"),
                        d.getContent()))
                .collect(Collectors.joining("\n\n"));

        // 3. Gera resposta com Gemini
        String prompt = """
                CONTEXTO (documentos médicos):
                %s
                
                PERGUNTA DO MÉDICO: %s
                """.formatted(context, question);

        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 4. Extrai fontes únicas
        List<String> sources = docs.stream()
                .map(d -> "%s (%s)".formatted(
                        d.getMetadata().getOrDefault("fileName", "N/A"),
                        d.getMetadata().getOrDefault("source", "N/A")))
                .distinct()
                .toList();

        return new MedicalResponse(answer, sources);
    }
}
