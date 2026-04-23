import { useMetrics } from './hooks/useMetrics';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid,
  Tooltip, Legend, ResponsiveContainer, BarChart, Bar,
} from 'recharts';
import { Activity, AlertTriangle, Shield, Database, Wifi, WifiOff } from 'lucide-react';

// ── Stat card ────────────────────────────────────────────────────────────────
function StatCard({
  label, value, sub, accent, Icon,
}: {
  label: string;
  value: string | number;
  sub?: string;
  accent: string;
  Icon: React.ElementType;
}) {
  return (
    <div className="bg-white border border-gray-100 rounded-xl p-5 flex items-start gap-4">
      <div className={`p-2 rounded-lg ${accent}`}>
        <Icon size={18} />
      </div>
      <div className="min-w-0">
        <p className="text-xs text-gray-400 mb-0.5 truncate">{label}</p>
        <p className="text-2xl font-medium text-gray-900 tabular-nums">{value}</p>
        {sub && <p className="text-xs text-gray-400 mt-0.5">{sub}</p>}
      </div>
    </div>
  );
}

// ── Redis badge ──────────────────────────────────────────────────────────────
function RedisBadge({ status }: { status: 'UP' | 'DOWN' | undefined }) {
  const up = status === 'UP';
  return (
    <span className={`flex items-center gap-1.5 text-xs px-3 py-1.5 rounded-full border font-medium
      ${up ? 'bg-green-50 border-green-200 text-green-700' : 'bg-red-50 border-red-200 text-red-600'}`}>
      {up ? <Wifi size={12} /> : <WifiOff size={12} />}
      Redis {status ?? '…'}
    </span>
  );
}

// ── App ──────────────────────────────────────────────────────────────────────
export default function App() {
  const { summary, algorithms, health, history, error } = useMetrics();

  const algoData = algorithms
    ? (['sliding-window', 'token-bucket', 'fixed-window'] as const).map(k => ({
        name: k.replace(/-/g, ' '),
        allowed: algorithms[k].allowed,
        denied:  algorithms[k].denied,
      }))
    : [];

  const fmt = (n: number | undefined) =>
    n !== undefined ? n.toLocaleString() : '—';

  return (
    <div className="min-h-screen bg-gray-50">
      {/* ── Header ── */}
      <header className="bg-white border-b border-gray-100 px-6 py-4 flex items-center justify-between">
        <div>
          <h1 className="text-base font-medium text-gray-900">Rate Limiter Dashboard</h1>
          <p className="text-xs text-gray-400 mt-0.5">3 nodes · Redis Sentinel · polling every 2s</p>
        </div>
        <RedisBadge status={health?.redis} />
      </header>

      <main className="p-6 space-y-5 max-w-6xl mx-auto">

        {/* ── Error banner ── */}
        {error && (
          <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800">
            Cannot reach API — {error}. Start the stack with{' '}
            <code className="font-mono text-xs bg-amber-100 px-1 rounded">docker compose up</code>.
          </div>
        )}

        {/* ── Stat cards ── */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <StatCard
            label="Requests allowed"
            value={fmt(summary?.totalAllowed)}
            accent="bg-green-100 text-green-700"
            Icon={Activity}
          />
          <StatCard
            label="Requests denied (429)"
            value={fmt(summary?.totalDenied)}
            sub="Too Many Requests"
            accent="bg-red-100 text-red-600"
            Icon={AlertTriangle}
          />
          <StatCard
            label="Allow rate"
            value={summary ? `${summary.allowRate.toFixed(1)}%` : '—'}
            accent="bg-blue-100 text-blue-700"
            Icon={Shield}
          />
          <StatCard
            label="Active client keys"
            value={fmt(summary?.activeKeys)}
            sub="tracked in Redis"
            accent="bg-purple-100 text-purple-700"
            Icon={Database}
          />
        </div>

        {/* ── Charts ── */}
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-4">

          {/* Time series — wider */}
          <div className="lg:col-span-3 bg-white border border-gray-100 rounded-xl p-5">
            <p className="text-sm font-medium text-gray-700 mb-1">Request rate</p>
            <p className="text-xs text-gray-400 mb-4">delta per 2s polling interval</p>
            <ResponsiveContainer width="100%" height={220}>
              <LineChart data={history} margin={{ top: 4, right: 4, bottom: 0, left: -24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                <XAxis dataKey="time" tick={{ fontSize: 10 }} interval="preserveStartEnd" />
                <YAxis tick={{ fontSize: 10 }} allowDecimals={false} />
                <Tooltip contentStyle={{ fontSize: 12 }} />
                <Legend iconSize={10} wrapperStyle={{ fontSize: 12 }} />
                <Line type="monotone" dataKey="allowed" stroke="#22c55e" strokeWidth={2} dot={false} name="Allowed" />
                <Line type="monotone" dataKey="denied"  stroke="#ef4444" strokeWidth={2} dot={false} name="Denied" />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Per-algorithm bar chart */}
          <div className="lg:col-span-2 bg-white border border-gray-100 rounded-xl p-5">
            <p className="text-sm font-medium text-gray-700 mb-1">By algorithm</p>
            <p className="text-xs text-gray-400 mb-4">cumulative totals</p>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={algoData} margin={{ top: 4, right: 4, bottom: 0, left: -24 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                <XAxis dataKey="name" tick={{ fontSize: 10 }} />
                <YAxis tick={{ fontSize: 10 }} allowDecimals={false} />
                <Tooltip contentStyle={{ fontSize: 12 }} />
                <Legend iconSize={10} wrapperStyle={{ fontSize: 12 }} />
                <Bar dataKey="allowed" fill="#22c55e" name="Allowed" radius={[3, 3, 0, 0]} />
                <Bar dataKey="denied"  fill="#ef4444" name="Denied"  radius={[3, 3, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* ── Algorithm info cards ── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {[
            {
              name: 'Sliding window',
              tag: 'default',
              desc: 'Interpolates the previous window\'s count weighted by the overlap fraction. O(1) memory, no boundary spike.',
              color: 'border-blue-200 bg-blue-50',
              badge: 'bg-blue-100 text-blue-700',
            },
            {
              name: 'Token bucket',
              tag: 'burst-friendly',
              desc: 'Tokens refill at a constant rate. Allows short bursts up to capacity while enforcing a long-term average rate.',
              color: 'border-purple-200 bg-purple-50',
              badge: 'bg-purple-100 text-purple-700',
            },
            {
              name: 'Fixed window',
              tag: 'educational',
              desc: 'Simplest implementation. Counter resets every N seconds. Included to demonstrate the boundary spike problem.',
              color: 'border-gray-200 bg-gray-50',
              badge: 'bg-gray-200 text-gray-600',
            },
          ].map(a => (
            <div key={a.name} className={`border rounded-xl p-4 ${a.color}`}>
              <div className="flex items-center gap-2 mb-2">
                <span className="text-sm font-medium text-gray-800">{a.name}</span>
                <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${a.badge}`}>{a.tag}</span>
              </div>
              <p className="text-xs text-gray-600 leading-relaxed">{a.desc}</p>
            </div>
          ))}
        </div>

        {/* ── Footer ── */}
        <p className="text-center text-xs text-gray-400 pb-2">
          Last updated: {summary ? new Date(summary.timestamp).toLocaleTimeString() : '—'}
        </p>

      </main>
    </div>
  );
}
