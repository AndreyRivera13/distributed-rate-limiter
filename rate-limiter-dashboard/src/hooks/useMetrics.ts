import { useState, useEffect, useRef } from 'react';
import type {
  MetricsSummary, AlgorithmsResponse, HealthResponse, TimeSeriesPoint,
} from '../types';

const API_BASE        = import.meta.env.VITE_API_URL ?? '';
const POLL_MS         = 2000;
const MAX_HISTORY     = 40;

async function fetchJson<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`);
  if (!res.ok) throw new Error(`HTTP ${res.status} on ${path}`);
  return res.json();
}

export function useMetrics() {
  const [summary,    setSummary]    = useState<MetricsSummary | null>(null);
  const [algorithms, setAlgorithms] = useState<AlgorithmsResponse | null>(null);
  const [health,     setHealth]     = useState<HealthResponse | null>(null);
  const [history,    setHistory]    = useState<TimeSeriesPoint[]>([]);
  const [error,      setError]      = useState<string | null>(null);

  const prevAllowed = useRef(0);
  const prevDenied  = useRef(0);

  useEffect(() => {
    let active = true;

    async function poll() {
      try {
        const [s, a, h] = await Promise.all([
          fetchJson<MetricsSummary>('/api/metrics/summary'),
          fetchJson<AlgorithmsResponse>('/api/metrics/algorithms'),
          fetchJson<HealthResponse>('/api/metrics/health'),
        ]);
        if (!active) return;

        setSummary(s);
        setAlgorithms(a);
        setHealth(h);
        setError(null);

        const deltaAllowed = Math.max(0, s.totalAllowed - prevAllowed.current);
        const deltaDenied  = Math.max(0, s.totalDenied  - prevDenied.current);
        prevAllowed.current = s.totalAllowed;
        prevDenied.current  = s.totalDenied;

        const time = new Date().toLocaleTimeString('en-US', { hour12: false });
        setHistory(prev => [...prev.slice(-(MAX_HISTORY - 1)), { time, allowed: deltaAllowed, denied: deltaDenied }]);
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : 'Unknown error');
      }
    }

    poll();
    const id = setInterval(poll, POLL_MS);
    return () => { active = false; clearInterval(id); };
  }, []);

  return { summary, algorithms, health, history, error };
}
