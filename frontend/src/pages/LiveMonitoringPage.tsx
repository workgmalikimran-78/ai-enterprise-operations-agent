import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import { SeverityBadge } from '../components/Badges'
import { Card } from '../components/StatCard'

export function LiveMonitoringPage() {
  const [service, setService] = useState('')

  const anomalies = useQuery({
    queryKey: ['anomalies', 'OPEN'],
    queryFn: () => api.anomalies.list('OPEN'),
    refetchInterval: 10000,
  })

  const errorLogs = useQuery({
    queryKey: ['logs', 'errors', service],
    queryFn: () => api.logs.search({ level: 'ERROR', service: service || undefined }),
    refetchInterval: 10000,
  })

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Live Monitoring</h1>
        <p className="text-sm text-slate-500 mt-1">Open anomalies and recent error events across applications</p>
      </div>

      <Card title="Anomaly Alerts">
        {anomalies.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {anomalies.data && anomalies.data.content.length === 0 && (
          <p className="text-sm text-slate-400">No open anomalies.</p>
        )}
        {anomalies.data && anomalies.data.content.length > 0 && (
          <div className="divide-y divide-slate-100">
            {anomalies.data.content.map((a) => (
              <div key={a.id} className="py-3 flex items-start justify-between gap-4">
                <div>
                  <p className="text-sm font-medium text-slate-800">{a.applicationName}</p>
                  <p className="text-sm text-slate-500 mt-0.5">{a.description}</p>
                  <p className="text-xs text-slate-400 mt-1">{a.detectionType} · {new Date(a.createdAt).toLocaleString()}</p>
                </div>
                <SeverityBadge severity={a.severity} />
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card>
        <div className="flex items-center justify-between mb-3">
          <h2 className="text-sm font-semibold text-slate-700">Recent Error Events</h2>
          <input
            value={service}
            onChange={(e) => setService(e.target.value)}
            placeholder="Filter by service…"
            className="text-sm border border-slate-300 rounded-md px-3 py-1.5 w-56 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
        </div>
        {errorLogs.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {errorLogs.data && errorLogs.data.content.length === 0 && (
          <p className="text-sm text-slate-400">No recent error logs.</p>
        )}
        {errorLogs.data && errorLogs.data.content.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-400 border-b border-slate-200">
                <th className="py-2 font-medium">Time</th>
                <th className="py-2 font-medium">Service</th>
                <th className="py-2 font-medium">Exception</th>
                <th className="py-2 font-medium">Message</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {errorLogs.data.content.map((log) => (
                <tr key={log.id}>
                  <td className="py-2 whitespace-nowrap text-slate-500">
                    {new Date(log.timestamp).toLocaleTimeString()}
                  </td>
                  <td className="py-2 font-medium text-slate-700">{log.service}</td>
                  <td className="py-2 text-slate-500">{log.exceptionType ?? '—'}</td>
                  <td className="py-2 text-slate-600 max-w-md truncate">{log.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
