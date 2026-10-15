# ETP Cloud Deployment Runbook: ECR, EKS & Microservices

A complete, clean-start runbook for building, pushing, and deploying the Enterprise Trading Platform (ETP) on AWS using Amazon Elastic Container Registry (ECR) and Amazon Elastic Kubernetes Service (EKS).

This unified runbook consolidates the container build workflow, AWS ECR private repository management, EKS cluster provisioning, storage driver setup, RDS PostgreSQL integration, Kubernetes Kustomize manifests, multi-service LoadBalancer provisioning, dynamic URL wiring, health verification, operations, and troubleshooting.

> **Execution Rule:** Run every command from the **application repository root** (`/home/ubuntu/projects/Chennai-Capstone-SE6-Team2`).

---

## Table of Contents

1. [Tested Architecture & Reference Values](#tested-architecture--reference-values)
2. [Phase 1: Environment & CLI Prerequisites](#phase-1-environment--cli-prerequisites)
3. [Phase 2: Amazon ECR Setup & Docker Authentication](#phase-2-amazon-ecr-setup--docker-authentication)
4. [Phase 3: Image Build, Tagging, and ECR Push](#phase-3-image-build-tagging-and-ecr-push)
5. [Phase 4: Provision EKS Cluster & EBS CSI Storage](#phase-4-provision-eks-cluster--ebs-csi-storage)
6. [Phase 5: RDS Network Security & Connectivity Verification](#phase-5-rds-network-security--connectivity-verification)
7. [Phase 6: Kubernetes Base Configuration & Secrets](#phase-6-kubernetes-base-configuration--secrets)
8. [Phase 7: Initial Kubernetes Deployment (Provision LoadBalancers)](#phase-7-initial-kubernetes-deployment-provision-loadbalancers)
9. [Phase 8: Wire LoadBalancer URLs & Rolling Restart](#phase-8-wire-loadbalancer-urls--rolling-restart)
10. [Phase 9: End-to-End Verification & Health Checks](#phase-9-end-to-end-verification--health-checks)
11. [Phase 10: Operational Management & Maintenance](#phase-10-operational-management--maintenance)
12. [Phase 11: Troubleshooting & Common Pitfalls](#phase-11-troubleshooting--common-pitfalls)
13. [Phase 12: Adaptation Guide for Other Projects / Clean Start](#phase-12-adaptation-guide-for-other-projects--clean-start)

---

## Tested Architecture & Reference Values

The following configuration parameters represent the verified deployment for **Team 2 (`Chennai-Capstone-SE6-Team2`)**:

```text
REPOSITORY_DIR=/home/ubuntu/projects/Chennai-Capstone-SE6-Team2
AWS_REGION=ap-south-1
AWS_ACCOUNT_ID=423333378978
CLUSTER_NAME=etp-cluster-2
NODEGROUP_NAME=workers
NODE_TYPE=t3.medium
DESIRED_NODES=2
MIN_NODES=2
MAX_NODES=3

# AWS RDS PostgreSQL
DB_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=trading
DB_USER=postgres
DB_SCHEMAS=tradingsystem, authentication, public
DB_SSL=true
PGSSLMODE=require

# Application Ports
AUTH_PORT=3000
ORDER_PORT=8081
EXECUTOR_PORT=8082
FRONTEND_PORT=80

# ECR Repositories & Local Contexts
AUTH_IMAGE=etp-auth-service        --> ./Services/auth-service
ORDER_IMAGE=etp-order-service      --> ./Services/trade-api
EXECUTOR_IMAGE=etp-executor-service --> ./Services/trade-executor
FRONTEND_IMAGE=etp-frontend        --> ./Frontend/trade-ui
```

Tested LoadBalancer DNS Endpoints:
```text
Frontend UI:         http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
Auth Service:        http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000
Order Service:       http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081
Executor Service:    http://a1b767e15d1744127a5d491014b2ff86-794669985.ap-south-1.elb.amazonaws.com:8082
```

---

## Phase 1: Environment & CLI Prerequisites

### 1.1 Verify Required Tools

Ensure the required command-line tools are installed:

```bash
aws --version
docker --version
kubectl version --client
eksctl version
```

### 1.2 Set Environment Variables & AWS Context

Run from the repository root:

```bash
cd /home/ubuntu/projects/Chennai-Capstone-SE6-Team2

# Target AWS Region
export AWS_REGION=ap-south-1
aws configure set region "$AWS_REGION"

# Cluster & Node Group
export CLUSTER_NAME=etp-cluster-2
export NODEGROUP_NAME=workers

# Database Context
export DB_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
export DB_PORT=5432
export DB_NAME=trading
export DB_USER=postgres

# Microservice Ports
export AUTH_PORT=3000
export ORDER_PORT=8081
export EXECUTOR_PORT=8082
export FRONTEND_PORT=80

# ECR Repository Names
export AUTH_IMAGE=etp-auth-service
export ORDER_IMAGE=etp-order-service
export EXECUTOR_IMAGE=etp-executor-service
export FRONTEND_IMAGE=etp-frontend

# Discover AWS Account ID dynamically (never hardcode in reusable scripts)
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

echo "Current AWS Account: $AWS_ACCOUNT_ID"
echo "Target ECR Registry: $REGISTRY"
```

---

## Phase 2: Amazon ECR Setup & Docker Authentication

### 2.1 Create Private ECR Repositories (Idempotent)

This command creates the four application repositories if they do not already exist, enabling vulnerability scanning on push and AES-256 server-side encryption:

```bash
set -e

for repo in "$AUTH_IMAGE" "$ORDER_IMAGE" "$EXECUTOR_IMAGE" "$FRONTEND_IMAGE"; do
  if aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$repo" >/dev/null 2>&1; then
    echo "ECR repository already exists: $repo"
  else
    echo "Creating ECR repository: $repo..."
    aws ecr create-repository \
      --region "$AWS_REGION" \
      --repository-name "$repo" \
      --image-scanning-configuration scanOnPush=true \
      --encryption-configuration encryptionType=AES256 \
      --query 'repository.repositoryUri' \
      --output text
  fi
done
```

### 2.2 Authenticate Docker with Amazon ECR

ECR authorization tokens expire every 12 hours. Authenticate Docker before building and pushing:

```bash
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"
```

---

## Phase 3: Image Build, Tagging, and ECR Push

### 3.1 Build the Four Application Images

You can build the images either directly via `docker build` or using `docker compose`:

#### Option A: Direct Docker Build (Recommended)
Each build context points to the exact service directory in this repository:

```bash
# 1. Auth Service (NestJS / Node.js BFF)
docker build -t etp-auth-service ./Services/auth-service

# 2. Order Service (Spring Boot Trade API)
docker build -t etp-order-service ./Services/trade-api

# 3. Executor Service (Spring Boot Trade Executor & Market Poller)
docker build -t etp-executor-service ./Services/trade-executor

# 4. Frontend UI (Angular on Nginx)
docker build -t etp-frontend ./Frontend/trade-ui
```

#### Option B: Docker Compose Build
```bash
docker compose build --no-cache
```

### 3.2 Tag and Push to Amazon ECR

#### Standard Deployment (`latest` Tag)
```bash
for image in etp-auth-service etp-order-service etp-executor-service etp-frontend; do
  docker tag "$image:latest" "$REGISTRY/$image:latest"
  docker push "$REGISTRY/$image:latest"
done
```

#### Immutable Production Deployment (Git Commit SHA)
For auditability and rollback safety, tag with the short commit hash:

```bash
IMAGE_TAG="$(git rev-parse --short HEAD)"
echo "Deploying with release tag: $IMAGE_TAG"

for image in etp-auth-service etp-order-service etp-executor-service etp-frontend; do
  docker tag "$image:latest" "$REGISTRY/$image:$IMAGE_TAG"
  docker push "$REGISTRY/$image:$IMAGE_TAG"
done
```

### 3.3 Verify Image Push in ECR

Query ECR to verify images and digests:

```bash
for repo in etp-auth-service etp-order-service etp-executor-service etp-frontend; do
  echo "=== ECR Repository: $repo ==="
  aws ecr describe-images \
    --region "$AWS_REGION" \
    --repository-name "$repo" \
    --query 'imageDetails[?imageTags && contains(imageTags, `latest`)].{Digest:imageDigest,Pushed:imagePushedAt,Tags:imageTags}' \
    --output table
done
```

### 3.4 Synchronize Image References in Kustomize

Ensure [`k8s/base/kustomization.yaml`](base/kustomization.yaml) points to your AWS Account ID and region:

```bash
grep -E 'newName:' k8s/base/kustomization.yaml
```

Expected format:
```yaml
images:
  - name: etp-auth-service
    newName: <AWS_ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/etp-auth-service
    newTag: latest
  - name: etp-order-service
    newName: <AWS_ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/etp-order-service
    newTag: latest
  - name: etp-executor-service
    newName: <AWS_ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/etp-executor-service
    newTag: latest
  - name: etp-frontend
    newName: <AWS_ACCOUNT_ID>.dkr.ecr.ap-south-1.amazonaws.com/etp-frontend
    newTag: latest
```

---

## Phase 4: Provision EKS Cluster & EBS CSI Storage

### 4.1 Create the EKS Cluster

The tested specifications are stored in [`k8s/cluster.example.yaml`](cluster.example.yaml). Create the cluster using `eksctl`:

```bash
eksctl create cluster -f k8s/cluster.example.yaml
```

Update your local `kubectl` context:

```bash
aws eks update-kubeconfig \
  --region "$AWS_REGION" \
  --name "$CLUSTER_NAME"

kubectl config current-context
kubectl get nodes -o wide
```

Expected status: 2 worker nodes in `Ready` state.

### 4.2 Install EBS CSI Storage Driver

Kafka KRaft requires EBS gp3 persistent volumes. Install the EKS-managed addon:

```bash
eksctl create addon \
  --cluster "$CLUSTER_NAME" \
  --region "$AWS_REGION" \
  --name aws-ebs-csi-driver \
  --version v1.66.0-eksbuild.1 \
  --force
```

Verify the driver pods and apply the gp3 StorageClass:

```bash
kubectl get pods -n kube-system | grep ebs
kubectl apply -f k8s/base/storageclass.yaml
kubectl get storageclass gp3
```

*(Fallback: If OIDC/IAM permissions are missing, attach the EBS CSI policy directly to the node role):*

```bash
NODEGROUP=$(aws eks list-nodegroups \
  --region "$AWS_REGION" \
  --cluster-name "$CLUSTER_NAME" \
  --query 'nodegroups[0]' --output text)

NODE_ROLE_ARN=$(aws eks describe-nodegroup \
  --region "$AWS_REGION" \
  --cluster-name "$CLUSTER_NAME" \
  --nodegroup-name "$NODEGROUP" \
  --query 'nodegroup.nodeRole' --output text)

NODE_ROLE_NAME="${NODE_ROLE_ARN##*/}"

aws iam attach-role-policy \
  --role-name "$NODE_ROLE_NAME" \
  --policy-arn arn:aws:iam::aws:policy/service-role/AmazonEBSCSIDriverPolicy
```

---

## Phase 5: RDS Network Security & Connectivity Verification

### 5.1 Authorize EKS Node Security Group in RDS

Authorize the EKS worker nodes to reach RDS PostgreSQL on port `5432`. Discover the security group IDs dynamically:

```bash
RDS_INSTANCE_ID=$(aws rds describe-db-instances \
  --region "$AWS_REGION" \
  --query "DBInstances[?Endpoint.Address=='$DB_HOST'].DBInstanceIdentifier | [0]" \
  --output text)

NODE_INSTANCE_ID=$(aws ec2 describe-instances \
  --region "$AWS_REGION" \
  --filters "Name=tag:eks:nodegroup-name,Values=$NODEGROUP_NAME" \
            "Name=instance-state-name,Values=running" \
  --query 'Reservations[].Instances[0].InstanceId | [0]' \
  --output text)

RDS_SECURITY_GROUP_ID=$(aws rds describe-db-instances \
  --region "$AWS_REGION" \
  --db-instance-identifier "$RDS_INSTANCE_ID" \
  --query 'DBInstances[0].VpcSecurityGroups[0].VpcSecurityGroupId' \
  --output text)

EKS_NODE_SECURITY_GROUP_ID=$(aws ec2 describe-instances \
  --region "$AWS_REGION" \
  --instance-ids "$NODE_INSTANCE_ID" \
  --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' \
  --output text)

echo "Adding RDS Security Group Ingress Rule..."
aws ec2 authorize-security-group-ingress \
  --group-id "$RDS_SECURITY_GROUP_ID" \
  --protocol tcp \
  --port 5432 \
  --source-group "$EKS_NODE_SECURITY_GROUP_ID" || \
  echo 'Ingress rule already exists.'
```

### 5.2 Test In-Cluster RDS Connectivity

Verify connectivity directly from within the Kubernetes cluster using a temporary container. The password is typed interactively and never written into shell history:

```bash
kubectl run rds-test --rm -it --restart=Never \
  --image=postgres:16 \
  --env="DB_HOST=$DB_HOST" \
  --env="DB_PORT=$DB_PORT" \
  --env="DB_NAME=$DB_NAME" \
  --env="DB_USER=$DB_USER" \
  -- bash -c 'read -rsp "Enter RDS Password: " PGPASSWORD; echo; PGPASSWORD="$PGPASSWORD" pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"'
```

Expected output: `<DB_HOST>:<DB_PORT> - accepting connections`.

---

## Phase 6: Kubernetes Base Configuration & Secrets

### 6.1 Generate Shared Application Configuration

Copy the centralized Spring configuration file into the base generated directory:

```bash
mkdir -p k8s/base/generated
cp config/application.yaml k8s/base/generated/application.yml
```

### 6.2 Set Up Non-Secret Settings (`k8s/base/config.env`)

Verify that [`k8s/base/config.env`](base/config.env) contains the correct database and internal service parameters:

```env
# Database Settings
DB_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=trading
DB_USER=postgres
DB_SCHEMA_TRADE=tradingsystem
DB_SCHEMA_AUTH=authentication
DB_SSL=true
PGSSLMODE=require
JDBC_PARAMS=&sslmode=require
SPRING_DATASOURCE_URL=jdbc:postgresql://database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com:5432/trading?currentSchema=tradingsystem,public&sslmode=require
POSTGRES_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
POSTGRES_PORT=5432
POSTGRES_DB=trading
POSTGRES_USER=postgres
DB_URL=jdbc:postgresql://database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com:5432/trading
DATABASE_URL=jdbc:postgresql://database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com:5432/trading
DB_DRIVER=org.postgresql.Driver

# Kafka Broker & Topics
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
KAFKA_MAX_BLOCK_MS=3000
TRADE_EVENTS_TOPIC=trade-events
MARKET_DATA_TOPIC=market-data
KAFKA_TOPICS=orders:3:604800000 orders.DLT:3:604800000 trade-events:3:2592000000 trade-events.DLT:3:2592000000 trade-signals:3:604800000 trade-signals.DLT:3:604800000 market-data:6:86400000 market-data.DLT:6:86400000
KAFKA_CONSUMER_GROUP_ID=trade-executor

# Ports
AUTH_SERVICE_PORT=3000
ORDER_SERVICE_PORT=8081
EXECUTOR_SERVICE_PORT=8082
FRONTEND_PORT=80
```

### 6.3 Set Up Kubernetes Secrets (`k8s/base/secrets.env`)

> [!CAUTION]
> Never commit `secrets.env` to Git. This file must stay local or be injected via a secrets manager.

Create `k8s/base/secrets.env`:

```bash
cat << 'EOF' > k8s/base/secrets.env
DB_PASSWORD=password123
POSTGRES_PASSWORD=password123
JWT_SECRET=development-only-shared-secret-change-me
INTERNAL_SERVICE_TOKEN=dev-internal-token
FAUXNANCE_API_KEY=fnx_dev_RspfljYfSDjur4Dbl5OrXJiifB0H71Sz
GEMINI_API_KEY=AQ.Ab8RN6KLPLUnbJNIElsce8VQbVsLDqL5U_P91kbhX7qUvzfbEA
LLM_API_KEY=AQ.Ab8RN6KLPLUnbJNIElsce8VQbVsLDqL5U_P91kbhX7qUvzfbEA
SMTP_USER=aakashkumary759@gmail.com
SMTP_PASS=xddr lfsa ludm kppy
MAIL_PASSWORD=xddr lfsa ludm kppy
EOF
```

Verify that `k8s/base/secrets.env` is ignored by Git:

```bash
git check-ignore k8s/base/secrets.env
```

---

## Phase 7: Initial Kubernetes Deployment (Provision LoadBalancers)

### 7.1 Dry-Run Validation

Verify the generated Kubernetes manifests:

```bash
kubectl kustomize k8s/overlays/four-lb > /tmp/etp-rendered.yaml
kubectl apply --dry-run=server -k k8s/overlays/four-lb
```

Confirm that Kubernetes will pull the images from your ECR registry:

```bash
kubectl kustomize k8s/overlays/four-lb \
  | grep -E 'image: .*ecr.*etp-(auth-service|order-service|executor-service|frontend)'
```

### 7.2 Apply the Deployment

Apply the `four-lb` overlay:

```bash
kubectl apply -k k8s/overlays/four-lb
```

Wait for AWS Elastic Load Balancers to provision external DNS hostnames:

```bash
kubectl get svc
```

Sample output:
```text
NAME               TYPE           EXTERNAL-IP
auth-service       LoadBalancer   a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com
executor-service   LoadBalancer   a1b767e15d1744127a5d491014b2ff86-794669985.ap-south-1.elb.amazonaws.com
frontend           LoadBalancer   a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
order-service      LoadBalancer   a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com
```

---

## Phase 8: Wire LoadBalancer URLs & Rolling Restart

### 8.1 Update `k8s/overlays/four-lb/urls.env`

Fill [`k8s/overlays/four-lb/urls.env`](overlays/four-lb/urls.env) with the generated LoadBalancer DNS names:

```env
FRONTEND_URL=http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
AUTH_SERVICE_URL=http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000
ORDER_SERVICE_URL=http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081
EXECUTOR_SERVICE_URL=http://a1b767e15d1744127a5d491014b2ff86-794669985.ap-south-1.elb.amazonaws.com:8082
CORS_ALLOWED_ORIGINS=http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
AUTH_API_URL=http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000
FRONTEND_API_URL=http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081
URL_REWRITES=http://localhost:3000=http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000 http://localhost:8081=http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081
```

### 8.2 Trigger Rolling Restart

Applying the updated overlay generates a new hash for the `etp-urls` ConfigMap, which automatically triggers a rolling update of all pods:

```bash
kubectl apply -k k8s/overlays/four-lb
```

Track rollout progress:

```bash
kubectl rollout status deployment/auth-service --timeout=180s
kubectl rollout status deployment/order-service --timeout=180s
kubectl rollout status deployment/executor-service --timeout=180s
kubectl rollout status deployment/frontend --timeout=180s
```

Check the active pods:

```bash
kubectl get pods -w
```

Expected output:
```text
NAME                                READY   STATUS      RESTARTS   AGE
auth-service-fcf9d7cb4-4bklq        1/1     Running     0          2m
executor-service-79bdf744cc-8npwg   1/1     Running     0          2m
frontend-5c4f8f9fc4-rv7x8           1/1     Running     0          2m
kafka-0                             1/1     Running     0          30m
kafka-topics-v8rd2                  0/1     Completed   0          30m
order-service-759c5446f4-j76f7      1/1     Running     0          2m
```

---

## Phase 9: End-to-End Verification & Health Checks

### 9.1 Test Live Endpoints

Test the live services directly using `curl`:

```bash
# 1. Order Service Health Endpoint
curl -i http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081/health

# 2. Auth Service Swagger Documentation
curl -i http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000/docs

# 3. Auth Service Health JSON Probe
curl -i http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000/docs-json

# 4. Frontend UI HTTP Header
curl -I http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
```

### 9.2 Verify Kafka Topics

Ensure all required Kafka topics are initialized on the broker:

```bash
kubectl exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --list
```

Expected topics:
- `orders`, `orders.DLT`
- `trade-events`, `trade-events.DLT`
- `trade-signals`, `trade-signals.DLT`
- `market-data`, `market-data.DLT`

### 9.3 In-Browser Testing

Open the Frontend URL in your browser:
`http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com`

1. Log in or create an account.
2. Verify portfolio data loads.
3. Place a buy order and check that execution completes without delay.

---

## Phase 10: Operational Management & Maintenance

### 10.1 Manually Restart Deployments
```bash
kubectl rollout restart deployment/auth-service
kubectl rollout restart deployment/order-service
kubectl rollout restart deployment/executor-service
kubectl rollout restart deployment/frontend
```

### 10.2 Stream Service Logs
```bash
# Order Service
kubectl logs -f deployment/order-service

# Auth Service
kubectl logs -f deployment/auth-service

# Executor Service
kubectl logs -f deployment/executor-service

# Frontend (Nginx access and error logs)
kubectl logs -f deployment/frontend
```

### 10.3 Stop Local Docker Compose Stack
To avoid PostgreSQL connection pool exhaustion on a shared RDS instance, shut down local containers:

```bash
docker compose down
```

---

## Phase 11: Troubleshooting & Common Pitfalls

### 1. `error: no pg_hba.conf entry ... no encryption` (HTTP 500 on Login)
- **Root Cause:** AWS RDS PostgreSQL enforces SSL encrypted connections. If `DB_SSL=false`, the NestJS/Node `pg` client attempts an unencrypted connection, causing PostgreSQL to reject the handshake.
- **Resolution:** Set `DB_SSL=true` and `PGSSLMODE=require` in `k8s/base/config.env`, re-apply Kustomize, and restart `auth-service`:
  ```bash
  kubectl apply -k k8s/overlays/four-lb
  kubectl rollout restart deployment/auth-service
  ```

### 2. Browser Shows "CORS Error" on API Calls
- **Root Cause:** When a backend pod is unready (`0/1 Running` or `CrashLoopBackOff`), the AWS Load Balancer returns `HTTP 502 Bad Gateway` without the CORS response headers. The browser misinterprets the missing CORS headers on the 502 error as a CORS violation.
- **Resolution:** Inspect pod health (`kubectl get pods`) and logs (`kubectl logs -l app=auth-service`). Ensure probes in `k8s/base/auth-service.yaml` point to `/docs-json` (which returns HTTP 200). Once the pod is `1/1 Running`, NestJS answers requests with the configured `CORS_ALLOWED_ORIGINS`.

### 3. Orders Hang for 15-60s or Return 409 Conflict (`Duplicate order`)
- **Root Cause:** When an order is placed, `trade-api` commits the order and publishes to Kafka `orders`. Immediately after, `SignalEngine` attempts to publish the resulting trade signal to Kafka topic `trade-signals`. Because `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false` and `trade-signals` was missing from `kafka-topics-job.yaml`, the Kafka producer blocked on metadata lookup for 60 seconds (`max.block.ms`). The LoadBalancer or browser connection timed out and re-forwarded the request with the same `idempotencyKey`, which `trade-api` rejected as `409 Conflict: Duplicate order`.
- **Resolution:** Create the missing topics on the Kafka broker:
  ```bash
  kubectl exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --create --if-not-exists --topic trade-signals --partitions 3 --replication-factor 1 --config retention.ms=604800000 --config cleanup.policy=delete
  kubectl exec kafka-0 -- /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:29092 --create --if-not-exists --topic trade-signals.DLT --partitions 3 --replication-factor 1 --config retention.ms=604800000 --config cleanup.policy=delete
  ```
  And verify that `trade-signals` is declared in `k8s/base/kafka-topics-job.yaml`.

### 4. Docker Push Returns `no basic auth credentials`
- **Root Cause:** ECR authorization tokens expire after 12 hours.
- **Resolution:** Re-authenticate Docker using:
  ```bash
  aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"
  ```

---

## Phase 12: Adaptation Guide for Other Projects / Clean Start

When adapting this runbook to a new repository, cluster, or AWS account:

1. **Repository Context:** Update the service directories in `docker build` commands to match your local directory structure.
2. **Cluster Name & Region:** Set `CLUSTER_NAME` and `AWS_REGION` to match your environment. Update `k8s/cluster.example.yaml` accordingly.
3. **ECR Registry:** Discover the account ID dynamically via `aws sts get-caller-identity`. Update the `images[].newName` entries in [`k8s/base/kustomization.yaml`](base/kustomization.yaml).
4. **Database Endpoints:** Replace `DB_HOST`, `DB_NAME`, `DB_USER`, and `SPRING_DATASOURCE_URL` in [`k8s/base/config.env`](base/config.env) with the new RDS instance parameters.
5. **Secrets:** Create `k8s/base/secrets.env` from your project's secure password store. Never commit credentials to version control.
6. **LoadBalancer Endpoints:** After running `kubectl apply -k k8s/overlays/four-lb`, fetch the new AWS ELB hostnames via `kubectl get svc` and paste them into [`k8s/overlays/four-lb/urls.env`](overlays/four-lb/urls.env).
7. **Single-Origin Deployment (Ingress):** The 4-LoadBalancer setup is intended for initial testing. For production, migrate to [`k8s/overlays/ingress`](overlays/ingress) with an AWS Application Load Balancer (ALB) or NGINX Ingress controller so all services share one origin and cookies (e.g. refresh tokens) function without third-party cookie restrictions.

