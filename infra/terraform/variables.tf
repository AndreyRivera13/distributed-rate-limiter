variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "project" {
  description = "Resource name prefix"
  type        = string
  default     = "rate-limiter"
}

variable "instance_type" {
  description = "EC2 instance type for API nodes"
  type        = string
  default     = "t3.small"
}

variable "key_pair_name" {
  description = "Existing EC2 Key Pair name for SSH"
  type        = string
}

variable "ssh_cidr" {
  description = "CIDR allowed to SSH (use your IP: x.x.x.x/32)"
  type        = string
  default     = "0.0.0.0/0"
}

variable "ecr_image_url" {
  description = "Full ECR image URL (e.g. 123456789.dkr.ecr.us-east-1.amazonaws.com/rate-limiter:latest)"
  type        = string
}
