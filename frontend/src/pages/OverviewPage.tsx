import { useQuery } from '@tanstack/react-query'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api/client'
import { Card, StatCard } from '../components/StatCard'

function formatDuration(seconds: number | null): string {
  if (seconds === null || Number.isNaN(seconds)) return '—'
  if (seconds < 60) return `${Math.round(seconds)}s`
  if (seconds < 3600) return `${Math.round(seconds / 60)}m`
  return `${(seconds / 3600).toFixed(1)}h`
}

export function OverviewPage() {
  const overview = useQuery({ queryKey: ['dashboard', 'overview'], queryFn: api.dashboard.overview, refetchInterval: 15000 })
  const metrics = useQuery({ queryKey: ['dashboard', 'metrics'], queryFn: api.dashboard.metrics, refetchInterval: 15000 })

  const severityData = metrics.data
    ? Object.entries(metrics.data.incidentsBySeverity).map(([severity, count]) => ({ severity, count }))
    : []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Overview</h1>
        <p className="text-sm text-slate-500 mt-1">Platform-wide incident and AI operations summary</p>
      </div>

      {overview.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
      {overview.isError && <p className="text-sm text-red-600">Failed to load dashboard overview.</p>}

      {overview.data && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <StatCard label="Total Incidents" value={overview.data.totalIncidents} />
          <StatCard label="Critical Incidents" value={overview.data.criticalIncidents} />
          <StatCard label="Active Anomalies" value={overview.data.activeAnomalies} />
          <StatCard label="Resolved Incidents" value={overview.data.resolvedIncidents} />
          <StatCard label="Mean Time to Detect" value={formatDuration(overview.data.mttdSeconds)} />
          <StatCard label="Mean Time to Resolve" value={formatDuration(overview.data.mttrSeconds)} />
          <StatCard label="AI Investigations" value={overview.data.aiInvestigations} />
          <StatCard
            label="AI Investigation Failures"
            value={overview.data.aiInvestigationFailures}
            hint={
              overview.data.aiInvestigations > 0
                ? `${Math.round((overview.data.aiInvestigationFailures / overview.data.aiInvestigations) * 100)}% failure rate`
                : undefined
            }
          />
        </div>
      )}

      <Card title="Incidents by Severity">
        {severityData.length === 0 ? (
          <p className="text-sm text-slate-400">No incident data yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={240}>
            <BarChart data={severityData}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="severity" tick={{ fontSize: 12 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Bar dataKey="count" fill="#4f46e5" radius={[4, 4, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        )}
      </Card>
    </div>
  )
}
