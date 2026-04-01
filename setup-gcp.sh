#!/bin/bash
# ===========================================
# Setup da infraestrutura no Google Cloud
# Execute: bash setup-gcp.sh <PROJECT_ID>
# ===========================================

PROJECT_ID=$1
REGION="southamerica-east1"
DB_INSTANCE="medical-rag-db"
DB_NAME="medical_rag"
DB_USER="medical_app"
BUCKET="medical-rag-docs-${PROJECT_ID}"

if [ -z "$PROJECT_ID" ]; then
  echo "Uso: bash setup-gcp.sh <PROJECT_ID>"
  exit 1
fi

echo "🔧 Configurando projeto: $PROJECT_ID"
gcloud config set project $PROJECT_ID

# 1. Habilitar APIs
echo "📡 Habilitando APIs..."
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  sqladmin.googleapis.com \
  aiplatform.googleapis.com \
  storage.googleapis.com

# 2. Criar bucket GCS
echo "🪣 Criando bucket: $BUCKET"
gcloud storage buckets create gs://$BUCKET \
  --location=$REGION \
  --uniform-bucket-level-access

# 3. Criar instância Cloud SQL (PostgreSQL)
echo "🗄️ Criando Cloud SQL PostgreSQL..."
gcloud sql instances create $DB_INSTANCE \
  --database-version=POSTGRES_15 \
  --tier=db-f1-micro \
  --region=$REGION \
  --storage-size=10GB \
  --storage-auto-increase

# 4. Criar banco e usuário
echo "👤 Criando banco e usuário..."
gcloud sql databases create $DB_NAME --instance=$DB_INSTANCE

gcloud sql users create $DB_USER \
  --instance=$DB_INSTANCE \
  --password="TROQUE_ESTA_SENHA"

# 5. Habilitar pgvector
echo "🧩 Habilitando pgvector..."
gcloud sql connect $DB_INSTANCE --user=postgres <<EOF
CREATE EXTENSION IF NOT EXISTS vector;
\q
EOF

# 6. Criar Artifact Registry
echo "📦 Criando Artifact Registry..."
gcloud artifacts repositories create medical-rag \
  --repository-format=docker \
  --location=$REGION

# 7. Criar Service Account para GitHub Actions
echo "🔑 Criando Service Account..."
gcloud iam service-accounts create github-actions \
  --display-name="GitHub Actions"

SA_EMAIL="github-actions@${PROJECT_ID}.iam.gserviceaccount.com"

for ROLE in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser roles/cloudsql.client; do
  gcloud projects add-iam-policy-binding $PROJECT_ID \
    --member="serviceAccount:$SA_EMAIL" \
    --role="$ROLE"
done

gcloud iam service-accounts keys create gcp-key.json \
  --iam-account=$SA_EMAIL

echo ""
echo "✅ Setup completo!"
echo ""
echo "📋 Próximos passos:"
echo "1. Adicione os secrets no GitHub:"
echo "   - GCP_SA_KEY     → conteúdo do arquivo gcp-key.json"
echo "   - GCP_PROJECT_ID → $PROJECT_ID"
echo "   - DB_HOST        → (IP do Cloud SQL, veja: gcloud sql instances describe $DB_INSTANCE)"
echo "   - DB_NAME        → $DB_NAME"
echo "   - DB_USER        → $DB_USER"
echo "   - DB_PASSWORD    → a senha que você definiu"
echo "   - GCS_BUCKET     → $BUCKET"
echo ""
echo "2. DELETE o arquivo gcp-key.json após adicionar como secret!"
echo "   rm gcp-key.json"
