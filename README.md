# 🏥 Medical RAG

> Assistente médico inteligente com **RAG (Retrieval-Augmented Generation)** usando **Vertex AI Gemini**, **Cloud SQL pgvector** e **Google Cloud Run**.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green?logo=springboot)
![Google Cloud](https://img.shields.io/badge/Google%20Cloud-Run-blue?logo=googlecloud)
![Vertex AI](https://img.shields.io/badge/Vertex%20AI-Gemini-purple?logo=googlecloud)

---

## 📖 Sobre

Sistema que permite médicos fazerem perguntas em linguagem natural e receberem respostas baseadas em **documentos médicos reais** (bulas, protocolos, guidelines).

### Fluxo

```
Upload PDF (bula/protocolo) → Chunking → Embedding → pgvector
                                                        ↓
Médico pergunta → Busca vetorial → Contexto + Histórico + Gemini → Resposta com fontes
                      ↑                                                    ↓
                      └──────────── Memória de Conversa ←───────────────────┘
```

---

## 🏗 Arquitetura

```
┌─────────────────────────────────────────────────┐
│                   DOMAIN                         │
│  Model: MedicalDocument, MedicalResponse         │
│  Ports: QueryPort, IngestionPort, StoragePort    │
├─────────────────────────────────────────────────┤
│               INFRASTRUCTURE                     │
│                                                  │
│  Inbound              Outbound                   │
│  ┌──────────────┐     ┌────────────────────┐     │
│  │ REST API     │     │ Vertex AI Gemini   │     │
│  │ - /api/query │     │ Vertex Embeddings  │     │
│  │ - /api/ingest│     │ Cloud SQL pgvector │     │
│  └──────────────┘     │ Google Cloud Storage│    │
│                       └────────────────────┘     │
└─────────────────────────────────────────────────┘
```

---

## 🛠 Tecnologias

| Tecnologia | Uso |
|---|---|
| Java 21 | Linguagem |
| Spring Boot 3.4.1 | Framework |
| Spring AI | Integração com IA |
| Vertex AI Gemini 1.5 Flash | LLM |
| Vertex AI Embeddings | Vetorização de texto |
| Cloud SQL PostgreSQL + pgvector | Vector store |
| Google Cloud Storage | Armazenamento de PDFs |
| Google Cloud Run | Hospedagem serverless |
| GitHub Actions | CI/CD |

---

## 🔌 Endpoints

### Health
| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/health` | Health check |

### Consulta
| Método | Endpoint | Descrição |
|---|---|---|
| `GET` | `/api/query?question={pergunta}&specialty={especialidade}&sessionId={sessão}` | Pergunta ao assistente |

| Parâmetro | Obrigatório | Descrição |
|---|---|---|
| `question` | Sim | Pergunta em linguagem natural |
| `specialty` | Não | Filtrar por especialidade médica |
| `sessionId` | Não | ID da sessão para manter contexto entre perguntas |

**Exemplo (pergunta isolada):**
```
GET /api/query?question=Posso prescrever ibuprofeno para paciente que toma varfarina?
```

**Exemplo (conversa contínua com sessionId):**
```
GET /api/query?question=Posso prescrever ibuprofeno para paciente que toma varfarina?&sessionId=medico-123
GET /api/query?question=E se o paciente for idoso?&sessionId=medico-123
GET /api/query?question=Qual alternativa mais segura nesse caso?&sessionId=medico-123
```

### Ingestão
| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/ingest/upload` | Upload de PDF + ingestão |
| `POST` | `/api/ingest/gcs` | Ingerir PDF já no GCS |

**Upload:**
```bash
curl -X POST http://localhost:8080/api/ingest/upload \
  -F "file=@bula-ibuprofeno.pdf" \
  -F "source=Anvisa" \
  -F "type=bula" \
  -F "specialty=geral"
```

---

## ☁️ Setup Google Cloud

### Pré-requisitos
- Google Cloud CLI instalado
- Conta com billing ativo

### Automático
```bash
bash setup-gcp.sh <SEU_PROJECT_ID>
```

### GitHub Secrets
| Secret | Descrição |
|---|---|
| `GCP_SA_KEY` | JSON da Service Account |
| `GCP_PROJECT_ID` | ID do projeto |
| `DB_HOST` | IP do Cloud SQL |
| `DB_NAME` | `medical_rag` |
| `DB_USER` | `medical_app` |
| `DB_PASSWORD` | Senha do banco |
| `GCS_BUCKET` | Nome do bucket |

---

## 🚀 Como rodar localmente

### 1. Clone
```bash
git clone <repo-url>
cd medical-rag
```

### 2. Variáveis de ambiente
```powershell
$env:GCP_PROJECT_ID="seu-project-id"
$env:GCP_LOCATION="southamerica-east1"
$env:DB_HOST="localhost"
$env:DB_NAME="medical_rag"
$env:DB_USER="postgres"
$env:DB_PASSWORD="postgres"
$env:GCS_BUCKET="medical-rag-docs"
```

### 3. PostgreSQL local com pgvector
```bash
docker run -d --name pgvector \
  -e POSTGRES_DB=medical_rag \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg15
```

### 4. Execute
```bash
mvn spring-boot:run
```

### 5. Acesse
- API: http://localhost:8080
- Swagger: http://localhost:8080/swagger-ui.html

---

## 💰 Custo estimado

| Componente | Custo/mês |
|---|---|
| Cloud Storage (20 GB) | ~US$ 0,40 |
| Cloud SQL (db-f1-micro) | ~US$ 30 |
| Gemini 1.5 Flash | ~US$ 1-5 |
| Cloud Run | ~US$ 0-5 |
| **Total** | **~US$ 35-40/mês** |

---

## 📚 Base Documental

Documentos médicos coletados de fontes oficiais do Ministério da Saúde para alimentar o RAG.

### Fontes

| Fonte | URL | Descrição |
|---|---|---|
| **Anvisa - Bulário** | https://consultas.anvisa.gov.br/#/bulario/ | Bulas de medicamentos registrados no Brasil |
| **CONITEC** | https://www.gov.br/conitec/ | Protocolos clínicos e relatórios de incorporação de tecnologias no SUS |
| **DATASUS** | https://servicos-datasus.saude.gov.br/ | Sistemas e dados do Ministério da Saúde |
| **RENAME** | https://www.gov.br/saude/pt-br/composicao/sectics/daf/rename | Relação Nacional de Medicamentos Essenciais do SUS |
| **OMS** | https://www.who.int/publications | Guidelines clínicos internacionais |
| **NICE (UK)** | https://www.nice.org.uk/guidance | Guidelines clínicos do Reino Unido |
| **PubMed** | https://pubmed.ncbi.nlm.nih.gov/ | Artigos científicos open access |
| **SciELO** | https://www.scielo.br/ | Artigos científicos brasileiros |

### Relatório de Download

| Categoria | Arquivos | Tamanho |
|---|---|---|
| **PCDTs** (Protocolos Clínicos e Diretrizes Terapêuticas) | 231 | 306 MB |
| **CONITEC** (relatórios, resoluções, enquetes) | 488 | 2.089 MB |
| **RENAME** (medicamentos essenciais) | 6 | 8,5 MB |
| **Outros** (diretrizes, metodologias, etc) | 395 | 553 MB |
| **Total** | **1.120** | **~3 GB** |

### Ingestão em lote

Após subir os PDFs para o GCS, usar o endpoint batch:

```bash
curl -X POST "http://localhost:8080/api/ingest/batch?prefix=documents/pcdt/&source=CONITEC&type=protocolo&specialty=geral"
curl -X POST "http://localhost:8080/api/ingest/batch?prefix=documents/conitec/&source=CONITEC&type=relatorio&specialty=geral"
curl -X POST "http://localhost:8080/api/ingest/batch?prefix=documents/rename/&source=Ministerio da Saude&type=rename&specialty=geral"
```

---

## 💬 Memória de Conversa

O sistema mantém **histórico de conversa por sessão**, permitindo diálogos contínuos onde o médico pode fazer perguntas de acompanhamento sem repetir o contexto.

### Como funciona

- **API REST**: passe o parâmetro `sessionId` para manter contexto entre chamadas
- **Telegram**: automático — cada chat do Telegram já mantém seu próprio histórico
- O histórico armazena as últimas **10 interações** (pergunta + resposta) por sessão
- Sem `sessionId`, cada pergunta é tratada de forma isolada (comportamento anterior)
- Armazenamento in-memory (`ConcurrentHashMap`) — histórico é perdido ao reiniciar o serviço

### Exemplo de conversa no Telegram

```
Médico: Qual o protocolo de tratamento para diabetes tipo 2?
Bot:    [resposta com fontes dos PCDTs]

Médico: E se o paciente tiver insuficiência renal?
Bot:    [resposta considerando diabetes + insuficiência renal]

Médico: Posso usar metformina nesse caso?
Bot:    [resposta contextualizada com todo o histórico]
```

### Arquivos alterados

| Arquivo | Alteração |
|---|---|
| `QueryPort.java` | Novo método `query(question, specialty, sessionId)` |
| `GeminiQueryAdapter.java` | Chat history in-memory por sessionId com `ConcurrentHashMap` |
| `QueryController.java` | Novo parâmetro opcional `sessionId` |
| `TelegramWebhookController.java` | Passa `chatId` como sessionId automaticamente |

---

## 🎓 MBA Full Cycle — Engenharia de Software com IA

Este projeto é desenvolvido aplicando conceitos do **MBA em Engenharia de Software com IA** da Full Cycle.

### ✅ Tópicos já aplicados no projeto

| Tópico | Disciplina do MBA | Implementação no Medical RAG |
|---|---|---|
| **RAG (Retrieval-Augmented Generation)** | Fundamentos de IA / Aplicações com IA | Busca vetorial → contexto → Gemini (`GeminiQueryAdapter`) |
| **Embeddings e representações vetoriais** | Fundamentos de IA | Vertex AI Embeddings `textembedding-gecko@003` |
| **Banco de dados vetorial** | Arquitetura na era da IA | Cloud SQL PostgreSQL + pgvector com índice HNSW |
| **Chunking e organização semântica** | Aplicações com IA (RAG) | `TokenTextSplitter` — 500 tokens, 100 overlap (`PgVectorIngestionAdapter`) |
| **Ingestão de fontes externas (PDFs)** | Aplicações com IA (RAG) | `PagePdfDocumentReader` + Google Cloud Storage |
| **LLM (modelo de linguagem)** | Fundamentos de IA | Vertex AI Gemini 2.5 Flash, temperatura 0.2 |
| **Memória de conversa** | Aplicações com IA (LangChain) | `ConcurrentHashMap` com histórico por sessionId — 10 interações |
| **System Prompt** | Prompt Engineering | Prompt com regras para o assistente médico (`SYSTEM_PROMPT`) |
| **Arquitetura Hexagonal (Ports & Adapters)** | Arquitetura na era da IA | `domain/port` + `infrastructure/adapter` (inbound/outbound) |
| **Cloud Providers e serviços-chave** | Arquitetura na era da IA | GCP: Cloud Run, Cloud SQL, GCS, Vertex AI, Pub/Sub |
| **Mensageria e processamento distribuído** | Arquitetura na era da IA | Google Cloud Pub/Sub para ingestão em lote |
| **API REST** | Desenvolvimento de Software | Spring Boot + Swagger/OpenAPI |
| **CI/CD** | DevOps e SRE com IA | GitHub Actions → Cloud Run |
| **Logging estruturado** | DevOps e SRE com IA | SLF4J + MDC + Logstash JSON encoder |
| **Busca com filtros por metadata** | Aplicações com IA (RAG) | `FilterExpressionBuilder` por specialty |
| **Integração com chat (bot)** | Aplicações com IA | Telegram Webhook com sessão automática por chatId |

### 🔜 Tópicos a implementar

| Tópico | Disciplina do MBA | O que será feito |
|---|---|---|
| **Prompt Engineering avançado** | Prompt Engineering | Chain of Thought, few-shot, ReAct, versionamento de prompts |
| **Caching de tokens e embeddings** | Arquitetura na era da IA | Cache de respostas e embeddings para reduzir custo e latência |
| **Agentes autônomos** | Desenvolvimento de Agentes | Agentes que planejam, decidem e executam ações médicas |
| **Orquestração de agentes** | Desenvolvimento de Agentes | LangChain, LangGraph, CrewAI ou Google ADK |
| **MCP (Model Context Protocol)** | Protocolos de Comunicação | Servidor MCP para expor ferramentas médicas |
| **A2A (Agent to Agent)** | Protocolos de Comunicação | Comunicação entre agentes especializados |
| **Guardrails e segurança LLM** | Arquitetura na era da IA | Proteção contra prompt injection, jailbreaking, OWASP Top 10 LLM |
| **Re-ranking de resultados** | Aplicações com IA (RAG) | Reordenação por relevância após busca vetorial |
| **Observabilidade com IA** | DevOps e SRE com IA | Métricas de tokens, latência, custo por request |
| **Controle de custos** | Arquitetura na era da IA | Tracking de input/output tokens, otimização de prompts |
| **Testes de prompts** | Arquitetura na era da IA | Testes automatizados de qualidade de resposta |
| **Design Docs** | Design Docs com IA | ADRs, RFCs, System Design docs |
| **DevSecOps** | DevOps e SRE com IA | SAST/DAST no pipeline, análise de vulnerabilidades |
| **Context Engineering** | Arquitetura na era da IA | Contexto dinâmico, versionamento, otimização por custo |
| **Fine-tuning** | Fundamentos de IA | Modelo especializado para domínio médico brasileiro |

---

## 🔜 Próximos Passos — Base Documental

- [ ] **Bulas da Anvisa** — Scraping do bulário eletrônico para obter bulas com interações medicamentosas, contraindicações e posologia
- [ ] **Guidelines da OMS** — Diretrizes clínicas internacionais para casos mais complexos
- [ ] **Guidelines do NICE (UK)** — Protocolos clínicos detalhados do Reino Unido
- [ ] **Artigos SciELO** — Artigos científicos brasileiros open access
- [ ] **Artigos PubMed** — Artigos científicos internacionais (filtrar por Free Full Text)
- [ ] **Formulário Terapêutico Nacional** — Doses, indicações e interações dos medicamentos do SUS
- [ ] **Resoluções do CFM** — Pareceres e diretrizes éticas do Conselho Federal de Medicina

---

## 📁 Estrutura

```
medical-rag/
├── .github/workflows/deploy.yml
├── src/main/java/com/medical/rag/
│   ├── domain/
│   │   ├── model/
│   │   │   ├── IngestionRequest.java
│   │   │   ├── MedicalDocument.java
│   │   │   └── MedicalResponse.java
│   │   └── port/
│   │       ├── IngestionPort.java
│   │       ├── QueryPort.java
│   │       └── StoragePort.java
│   ├── infrastructure/
│   │   ├── adapter/
│   │   │   ├── inbound/rest/
│   │   │   │   ├── HealthController.java
│   │   │   │   ├── IngestionController.java
│   │   │   │   ├── QueryController.java
│   │   │   │   └── TelegramWebhookController.java
│   │   │   └── outbound/
│   │   │       ├── llm/GeminiQueryAdapter.java
│   │   │       ├── storage/GcsStorageAdapter.java
│   │   │       ├── telegram/TelegramAdapter.java
│   │   │       └── vectorstore/PgVectorIngestionAdapter.java
│   │   └── config/
│   │       ├── GcsConfig.java
│   │       └── GlobalExceptionHandler.java
│   └── MedicalRagApplication.java
├── src/main/resources/application.yml
├── docs/
├── setup-gcp.sh
├── Dockerfile
├── pom.xml
└── README.md
```

---

## ⚠️ Débito Técnico

| # | Item | Prioridade | Descrição |
|---|---|---|---|
| 1 | **Rotacionar credenciais expostas** | 🔴 Crítica | `DB_PASSWORD` e `TELEGRAM_BOT_TOKEN` foram expostos em histórico de chat. Rotacionar imediatamente e atualizar nos GitHub Secrets e Cloud Run env vars. |
| 2 | **Secrets em variáveis de ambiente** | 🔴 Alta | Migrar credenciais sensíveis (DB_PASSWORD, TELEGRAM_BOT_TOKEN) para **Google Secret Manager** em vez de env vars diretas no Cloud Run. |
| 3 | **Memória de conversa in-memory** | 🟡 Média | `ConcurrentHashMap` perde histórico ao reiniciar. Migrar para Redis ou Cloud SQL para persistência entre deploys. |
| 4 | **Sem testes automatizados** | 🟡 Média | Projeto não possui testes unitários nem de integração. Adicionar testes para adapters, ports e fluxo RAG. |
| 5 | **Sem rate limiting** | 🟡 Média | API e bot Telegram não possuem controle de taxa de requisições. Risco de abuso e custos inesperados com Vertex AI. |
| 6 | **Sem autenticação na API** | 🟡 Média | Endpoints REST são públicos (`--allow-unauthenticated`). Avaliar adicionar API key ou OAuth para ambientes de produção. |
| 7 | **Sem validação de input** | 🟡 Média | Sem proteção contra prompt injection no input do usuário. Implementar sanitização e guardrails. |
| 8 | **Sem observabilidade de custos** | 🟠 Baixa | Sem tracking de tokens consumidos (input/output) por request. Dificulta controle de custos com Vertex AI. |
| 9 | **Sem health check do banco** | 🟠 Baixa | Endpoint `/health` não valida conexão com Cloud SQL nem pgvector. |
| 10 | **Duplicação de ingestão** | 🟠 Baixa | Verificação de duplicatas usa apenas `fileName` no metadata. Pode falhar se o mesmo PDF for re-uploadado com nome diferente. |
