output "alb_dns" {
  description = "Public URL of the load balancer"
  value       = "http://${aws_lb.main.dns_name}"
}

output "redis_primary_endpoint" {
  description = "ElastiCache primary endpoint (internal)"
  value       = aws_elasticache_replication_group.redis.primary_endpoint_address
}

output "smoke_test" {
  description = "Quick curl to verify the deployment"
  value       = "curl -i http://${aws_lb.main.dns_name}/api/v1/ping"
}
