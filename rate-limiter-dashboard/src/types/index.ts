export interface MetricsSummary {
  totalAllowed:  number;
  totalDenied:   number;
  totalBlocked:  number;
  activeKeys:    number;
  allowRate:     number;
  timestamp:     number;
}

export interface AlgorithmStats {
  allowed: number;
  denied:  number;
}

export interface AlgorithmsResponse {
  'sliding-window': AlgorithmStats;
  'token-bucket':   AlgorithmStats;
  'fixed-window':   AlgorithmStats;
}

export interface HealthResponse {
  status:    'UP' | 'DEGRADED';
  redis:     'UP' | 'DOWN';
  timestamp: number;
}

export interface TimeSeriesPoint {
  time:    string;
  allowed: number;
  denied:  number;
}
