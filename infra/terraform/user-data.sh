#!/bin/bash
set -euo pipefail

yum update -y
yum install -y docker aws-cli
systemctl enable --now docker

aws ecr get-login-password --region ${aws_region} \
  | docker login --username AWS --password-stdin ${ecr_image}

docker pull ${ecr_image}

docker run -d \
  --name rate-limiter-api \
  --restart unless-stopped \
  -p 8080:8080 \
  -e REDIS_HOST="${redis_host}" \
  -e REDIS_PORT="${redis_port}" \
  ${ecr_image}
