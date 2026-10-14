# Tested ETP Deployment Runbook

This is the filled-in runbook for the deployment that was tested and verified for **Team 2 (`Chennai-Capstone-SE6-Team2`)**.

The only values not stored in git are passwords, JWT secrets, and API keys. Store those in the git-ignored `k8s/base/secrets.env` before applying Kubernetes resources.

---

## Tested Values

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

# RDS PostgreSQL
DB_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=trading
DB_USER=postgres
DB_SCHEMAS=tradingsystem, authentication, public

# Service Ports
AUTH_PORT=3000
ORDER_PORT=8081
EXECUTOR_PORT=8082
FRONTEND_PORT=80

# ECR Repositories
AUTH_IMAGE=etp-auth-service
ORDER_IMAGE=etp-order-service
EXECUTOR_IMAGE=etp-executor-service
FRONTEND_IMAGE=etp-frontend
```

Tested LoadBalancer Endpoints:
```text
Frontend:         http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
Auth Service:     http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000
Order Service:    http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081
Executor Service: http://a1b767e15d1744127a5d491014b2ff86-794669985.ap-south-1.elb.amazonaws.com:8082
```

---

## 1. Set AWS Context & Environment Variables

Run from the repository root:

```bash
cd /home/ubuntu/projects/Chennai-Capstone-SE6-Team2

export AWS_REGION=ap-south-1
export CLUSTER_NAME=etp-cluster-2
export NODEGROUP_NAME=workers

# Database
export DB_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
export DB_PORT=5432
export DB_NAME=trading
export DB_USER=postgres

# Ports
export AUTH_PORT=3000
export ORDER_PORT=8081
export EXECUTOR_PORT=8082
export FRONTEND_PORT=80

# Image Names
export AUTH_IMAGE=etp-auth-service
export ORDER_IMAGE=etp-order-service
export EXECUTOR_IMAGE=etp-executor-service
export FRONTEND_IMAGE=etp-frontend

# Set AWS default region and verify identity
aws configure set region "$AWS_REGION"
aws sts get-caller-identity
```

---

## 2. Create the EKS Cluster

The tested cluster specifications are configured in [`k8s/cluster.example.yaml`](cluster.example.yaml).

Launch the cluster:

```bash
eksctl create cluster -f k8s/cluster.example.yaml
```

Update your local `kubectl` configuration:

```bash
aws eks update-kubeconfig \
  --region "$AWS_REGION" \
  --name "$CLUSTER_NAME"

kubectl config current-context
kubectl get nodes -o wide
```

Expected result: Two `Ready` worker nodes running in `ap-south-1`.

---

## 3. Install EBS CSI Storage

Kafka KRaft requires EBS persistent volume storage. Install the EKS-managed addon:

```bash
eksctl create addon \
  --cluster "$CLUSTER_NAME" \
  --region "$AWS_REGION" \
  --name aws-ebs-csi-driver \
  --version v1.66.0-eksbuild.1 \
  --force
```

Verify the storage driver and apply the gp3 storage class:

```bash
kubectl get pods -n kube-system | grep ebs
kubectl apply -f k8s/base/storageclass.yaml
kubectl get storageclass gp3
```

*(If OIDC/IAM permissions are missing, attach the EBS CSI policy to the node role):*

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

## 4. Configure RDS Security Group & Connectivity

Authorize the EKS worker nodes to communicate with RDS on port `5432`:

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

aws ec2 authorize-security-group-ingress \
  --group-id "$RDS_SECURITY_GROUP_ID" \
  --protocol tcp \
  --port 5432 \
  --source-group "$EKS_NODE_SECURITY_GROUP_ID" || \
  echo 'Ingress rule already exists.'
```

Test RDS connectivity from inside the cluster:

```bash
kubectl run rds-test --rm -it --restart=Never \
  --image=postgres:16 \
  --env="DB_HOST=$DB_HOST" \
  --env="DB_PORT=$DB_PORT" \
  --env="DB_NAME=$DB_NAME" \
  --env="DB_USER=$DB_USER" \
  -- bash -c 'read -rsp "RDS password: " PGPASSWORD; echo; PGPASSWORD="$PGPASSWORD" pg_isready -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME"'
```

---

## 5. Prepare Kubernetes Base Config & Secrets

### 5.1 Generate Shared Application Configuration
Copy the project configuration into the base generated folder:

```bash
mkdir -p k8s/base/generated
cp config/application.yaml k8s/base/generated/application.yml
```

### 5.2 Set Up `k8s/base/config.env`
Ensure [`k8s/base/config.env`](base/config.env) contains the correct database and internal service endpoints:

```env
DB_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=trading
DB_USER=postgres
POSTGRES_HOST=database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com
POSTGRES_PORT=5432
POSTGRES_DB=trading
POSTGRES_USER=postgres
DB_URL=jdbc:postgresql://database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com:5432/trading
DATABASE_URL=jdbc:postgresql://database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com:5432/trading
SPRING_DATASOURCE_URL=jdbc:postgresql://database-1.c7yocqkqi585.ap-south-1.rds.amazonaws.com:5432/trading?currentSchema=tradingsystem,public&sslmode=require
KAFKA_BOOTSTRAP_SERVERS=kafka:29092
```

### 5.3 Set Up `k8s/base/secrets.env` (Git-Ignored)
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

Verify that `secrets.env` is not tracked by Git:

```bash
test ! -e k8s/base/secrets.env || echo 'secrets.env is local only'
```

---

## 6. Build, Tag, and Push ECR Images

Discover the AWS account ID and log into ECR:

```bash
AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"

# Create ECR repositories if they do not exist
for repo in "$AUTH_IMAGE" "$ORDER_IMAGE" "$EXECUTOR_IMAGE" "$FRONTEND_IMAGE"; do
  aws ecr describe-repositories \
    --region "$AWS_REGION" \
    --repository-names "$repo" >/dev/null 2>&1 || \
  aws ecr create-repository \
    --region "$AWS_REGION" \
    --repository-name "$repo" \
    --image-scanning-configuration scanOnPush=true \
    --encryption-configuration encryptionType=AES256
done

# Authenticate Docker to ECR
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

# Build locally via Compose or script:
docker compose build --no-cache

# Tag and push images to ECR
declare -A IMAGE_MAP=(
  ["$AUTH_IMAGE"]=etp-auth-service
  ["$ORDER_IMAGE"]=etp-order-service
  ["$EXECUTOR_IMAGE"]=etp-executor-service
  ["$FRONTEND_IMAGE"]=etp-frontend
)

for repo in "${!IMAGE_MAP[@]}"; do
  docker tag "${IMAGE_MAP[$repo]}:latest" "$REGISTRY/$repo:latest"
  docker push "$REGISTRY/$repo:latest"
done
```

Verify that [`k8s/base/kustomization.yaml`](base/kustomization.yaml) points to your AWS Account ID:

```bash
grep -E 'newName:' k8s/base/kustomization.yaml
```
Expected output:
```text
newName: 423333378978.dkr.ecr.ap-south-1.amazonaws.com/etp-auth-service
newName: 423333378978.dkr.ecr.ap-south-1.amazonaws.com/etp-order-service
newName: 423333378978.dkr.ecr.ap-south-1.amazonaws.com/etp-executor-service
newName: 423333378978.dkr.ecr.ap-south-1.amazonaws.com/etp-frontend
```

---

## 7. Initial Deployment (Provision LoadBalancers)

Validate the rendered manifest and apply:

```bash
kubectl kustomize k8s/overlays/four-lb > /tmp/etp-rendered.yaml
kubectl apply --dry-run=server -k k8s/overlays/four-lb
kubectl apply -k k8s/overlays/four-lb
```

Wait for AWS to assign external DNS names to the services:

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

## 8. Configure LoadBalancer URLs & Rolling Restart

Update [`k8s/overlays/four-lb/urls.env`](overlays/four-lb/urls.env) with the generated addresses:

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

Apply the updated URLs (Kustomize generates a new hash for `etp-urls`, which automatically triggers a rolling restart):

```bash
kubectl apply -k k8s/overlays/four-lb
```

Watch the rollout:

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

## 9. Verification & Health Checks

Test the live endpoints directly:

```bash
# Order Service Health Probe
curl -i http://a1e63dc52a6ff420f8c0cccedc0e5212-451948130.ap-south-1.elb.amazonaws.com:8081/health

# Auth Service Swagger / Docs
curl -i http://a4fc364e5ee3f43be9e23e70853a5f3f-797349382.ap-south-1.elb.amazonaws.com:3000/docs

# Frontend UI
curl -I http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com
```

Open the Frontend URL in your browser:  
`http://a8514a6cbf6b04633a6b3302990970e8-1714256610.ap-south-1.elb.amazonaws.com`

---

## 10. Operational Commands & Maintenance

### Manually Restart Pods
```bash
kubectl rollout restart deployment/auth-service
kubectl rollout restart deployment/order-service
kubectl rollout restart deployment/executor-service
kubectl rollout restart deployment/frontend
```

### View Live Logs
```bash
kubectl logs -f deployment/order-service
kubectl logs -f deployment/auth-service
kubectl logs -f deployment/executor-service
```

### Delete Lingering Pods
If an older failed pod is retained:
```bash
kubectl delete pod <old-pod-name>
```

### Stop Local Compose Stack
To avoid exhausting PostgreSQL connection slots on the RDS instance, stop local containers:
```bash
docker compose down
```
EOF

