#!/bin/bash
# Prepara Google Cloud (proyecto `calarmi`) para que el CD publique en Google Play
# sin llaves: una cuenta de servicio sin llave JSON y Workload Identity Federation
# que solo acepta tokens del repo xMathyu/calarmi-android. Idempotente: se puede
# volver a correr. Después hay que invitar a la cuenta de servicio en Play Console
# (Usuarios y permisos) con permiso para publicar en pistas de prueba.
set -euo pipefail
PROJECT=calarmi
NUMBER=$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')
REPO=xMathyu/calarmi-android
SA=calarmi-play-publisher@$PROJECT.iam.gserviceaccount.com

gcloud services enable androidpublisher.googleapis.com iamcredentials.googleapis.com sts.googleapis.com --project "$PROJECT"

gcloud iam service-accounts describe "$SA" --project "$PROJECT" >/dev/null 2>&1 ||
  gcloud iam service-accounts create calarmi-play-publisher --project "$PROJECT" \
    --display-name "Calarmi: publicar en Google Play desde GitHub Actions"

gcloud iam workload-identity-pools describe github --project "$PROJECT" --location global >/dev/null 2>&1 ||
  gcloud iam workload-identity-pools create github --project "$PROJECT" --location global --display-name "GitHub Actions"

gcloud iam workload-identity-pools providers describe github --project "$PROJECT" --location global \
    --workload-identity-pool github >/dev/null 2>&1 ||
  gcloud iam workload-identity-pools providers create-oidc github --project "$PROJECT" --location global \
    --workload-identity-pool github --display-name "GitHub" \
    --issuer-uri https://token.actions.githubusercontent.com \
    --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition "assertion.repository == '$REPO'"

gcloud iam service-accounts add-iam-policy-binding "$SA" --project "$PROJECT" \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/$NUMBER/locations/global/workloadIdentityPools/github/attribute.repository/$REPO" \
  --condition None >/dev/null

# Identificadores, no secretos: el workflow los lee como variables.
gh variable set GCP_WIF_PROVIDER --repo "$REPO" \
  --body "projects/$NUMBER/locations/global/workloadIdentityPools/github/providers/github"
gh variable set GCP_PLAY_SA --repo "$REPO" --body "$SA"
echo "Listo. Falta invitar a $SA en Play Console › Usuarios y permisos."
