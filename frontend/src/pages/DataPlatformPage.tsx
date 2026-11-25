import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { api } from '../api/client'
import { ClassificationBadge, StatusBadge } from '../components/Badges'
import { Card, StatCard } from '../components/StatCard'

export function DataPlatformPage() {
  const queryClient = useQueryClient()
  const catalog = useQuery({ queryKey: ['warehouse', 'catalog'], queryFn: api.warehouse.catalog })
  const etlRuns = useQuery({ queryKey: ['warehouse', 'etl-runs'], queryFn: api.warehouse.etlRuns, refetchInterval: 15000 })
  const qualityChecks = useQuery({
    queryKey: ['warehouse', 'quality-checks'],
    queryFn: api.warehouse.qualityChecks,
    refetchInterval: 15000,
  })
  const incidentsDaily = useQuery({ queryKey: ['warehouse', 'incidents-daily'], queryFn: api.warehouse.incidentsDaily })
  const remediationDaily = useQuery({
    queryKey: ['warehouse', 'remediation-daily'],
    queryFn: api.warehouse.remediationDaily,
  })
  const logVolumeHourly = useQuery({
    queryKey: ['warehouse', 'log-volume-hourly'],
    queryFn: api.warehouse.logVolumeHourly,
  })

  const runEtl = useMutation({
    mutationFn: api.warehouse.runEtlNow,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['warehouse'] })
    },
  })

  const latestRun = etlRuns.data?.content[0]
  const latestRunChecks = qualityChecks.data?.content.filter((c) => c.etlRunId === latestRun?.id) ?? []

  const incidentsByDay = groupSum(incidentsDaily.data ?? [], (f) => f.dateId, (f) => f.incidentsOpened)
  const remediationByDay = (remediationDaily.data ?? []).reduce<Record<string, { date: string; succeeded: number; failed: number }>>(
    (acc, f) => {
      const row = (acc[f.dateId] ??= { date: f.dateId, succeeded: 0, failed: 0 })
      row.succeeded += f.executionsSucceeded
      row.failed += f.executionsFailed
      return acc
    },
    {},
  )
  const logVolumeByHour = groupSum(logVolumeHourly.data ?? [], (f) => new Date(f.hourBucket).toLocaleString(), (f) => f.eventCount)

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Data Platform</h1>
          <p className="text-sm text-slate-500 mt-1">
            Analytics warehouse, bronze data lake, ETL pipeline, and governance catalog
          </p>
        </div>
        <button
          onClick={() => runEtl.mutate()}
          disabled={runEtl.isPending}
          className="text-sm font-medium bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 disabled:opacity-50"
        >
          {runEtl.isPending ? 'Running ETL…' : 'Run ETL now'}
        </button>
      </div>

      {latestRun && (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-4">
          <StatCard label="Last ETL Run" value={<StatusBadge status={latestRun.status} />} />
          <StatCard label="Rows Extracted" value={latestRun.rowsExtracted} />
          <StatCard label="Rows Loaded" value={latestRun.rowsLoaded} />
          <StatCard
            label="Last Run At"
            value={latestRun.startedAt ? new Date(latestRun.startedAt).toLocaleString() : '—'}
          />
        </div>
      )}

      <Card title="Data Quality — Latest Run">
        {latestRunChecks.length === 0 && (
          <p className="text-sm text-slate-400">No quality check results yet — run the ETL job.</p>
        )}
        <div className="flex flex-wrap gap-3">
          {latestRunChecks.map((c) => (
            <div key={c.id} className="flex items-center gap-2 border border-slate-200 rounded-md px-3 py-2">
              <StatusBadge status={c.status} />
              <span className="text-sm text-slate-700">{c.checkName.replace(/_/g, ' ')}</span>
            </div>
          ))}
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Incidents Opened by Day (warehouse)">
          {incidentsByDay.length === 0 ? (
            <p className="text-sm text-slate-400">No warehouse data yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={incidentsByDay}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="key" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="value" fill="#4f46e5" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>

        <Card title="Remediation Outcomes by Day (warehouse)">
          {Object.keys(remediationByDay).length === 0 ? (
            <p className="text-sm text-slate-400">No warehouse data yet.</p>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={Object.values(remediationByDay)}>
                <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                <XAxis dataKey="date" tick={{ fontSize: 11 }} />
                <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Legend />
                <Bar dataKey="succeeded" stackId="a" fill="#059669" radius={[0, 0, 0, 0]} />
                <Bar dataKey="failed" stackId="a" fill="#dc2626" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
      </div>

      <Card title="Log Volume by Hour (warehouse)">
        {logVolumeByHour.length === 0 ? (
          <p className="text-sm text-slate-400">No warehouse data yet.</p>
        ) : (
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={logVolumeByHour}>
              <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
              <XAxis dataKey="key" tick={{ fontSize: 10 }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 12 }} />
              <Tooltip />
              <Line type="monotone" dataKey="value" stroke="#4f46e5" strokeWidth={2} dot={false} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </Card>

      <Card title="Data Catalog — Governance">
        {catalog.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {catalog.data && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-slate-400 border-b border-slate-200">
                  <th className="py-2 font-medium">Dataset</th>
                  <th className="py-2 font-medium">Layer</th>
                  <th className="py-2 font-medium">Classification</th>
                  <th className="py-2 font-medium">PII</th>
                  <th className="py-2 font-medium">Retention</th>
                  <th className="py-2 font-medium">Owner</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {catalog.data.map((entry) => (
                  <tr key={entry.id}>
                    <td className="py-2 font-medium text-slate-700">{entry.datasetName}</td>
                    <td className="py-2 text-slate-500">{entry.layer}</td>
                    <td className="py-2">
                      <ClassificationBadge classification={entry.classification} />
                    </td>
                    <td className="py-2 text-slate-500">{entry.containsPii ? 'Yes' : 'No'}</td>
                    <td className="py-2 text-slate-500">
                      {entry.retentionDays ? `${entry.retentionDays}d` : 'Indefinite'}
                    </td>
                    <td className="py-2 text-slate-500">{entry.owner}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  )
}

function groupSum<T>(items: T[], keyFn: (item: T) => string, valueFn: (item: T) => number) {
  const map = new Map<string, number>()
  for (const item of items) {
    const key = keyFn(item)
    map.set(key, (map.get(key) ?? 0) + valueFn(item))
  }
  return Array.from(map.entries()).map(([key, value]) => ({ key, value }))
}
