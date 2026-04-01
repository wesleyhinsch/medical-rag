package com.medical.rag.infrastructure.adapter.inbound.rest;

import com.medical.rag.domain.model.MedicalResponse;
import com.medical.rag.domain.port.QueryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query")
@Tag(name = "Consulta Médica", description = "Perguntas ao assistente médico com RAG")
public class QueryController {

    private final QueryPort queryPort;

    public QueryController(QueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @GetMapping
    @Operation(summary = "Consultar o assistente médico",
            description = "Faz uma pergunta e recebe resposta baseada em documentos médicos")
    public MedicalResponse query(
            @RequestParam String question,
            @RequestParam(required = false) String specialty) {
        return queryPort.query(question, specialty);
    }
}
