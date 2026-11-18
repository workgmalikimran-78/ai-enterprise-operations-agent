import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { SeverityBadge, StatusBadge } from '../components/Badges'
import { Card } from '../components/StatCard'

const STATUS_FILTERS = ['', 'OPEN', 'INVESTIGATING', 'MITIGATED', 'RESOLVED', 'CLOSED']

export function IncidentsListPage() {
  const [status, setStatus] = useState('')
  const incidents = useQuery({
    queryKey: ['incidents', status],
    queryFn: () => api.incidents.list(status || undefined),
    refetchInterval: 10000,
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Incidents</h1>
          <p className="text-sm text-slate-500 mt-1">All incidents opened by the anomaly detection engine</p>
        </div>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="text-sm border border-slate-300 rounded-md px-3 py-1.5 focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          {STATUS_FILTERS.map((s) => (
            <option key={s} value={s}>
              {s === '' ? 'All statuses' : s}
            </option>
          ))}
        </select>
      </div>

      <Card>
        {incidents.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {incidents.data && incidents.data.content.length === 0 && (
          <p className="text-sm text-slate-400">No incidents found.</p>
        )}
        {incidents.data && incidents.data.content.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-400 border-b border-slate-200">
                <th className="py-2 font-medium">Title</th>
                <th className="py-2 font-medium">Application</th>
                <th className="py-2 font-medium">Severity</th>
                <th className="py-2 font-medium">Status</th>
                <th className="py-2 font-medium">Opened</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {incidents.data.content.map((incident) => (
                <tr key={incident.id} className="hover:bg-slate-50">
                  <td className="py-2.5">
                    <Link to={`/incidents/${incident.id}`} className="font-medium text-indigo-700 hover:underline">
                      {incident.title}
                    </Link>
                  </td>
                  <td className="py-2.5 text-slate-600">{incident.applicationName}</td>
                  <td className="py-2.5">
                    <SeverityBadge severity={incident.severity} />
                  </td>
                  <td className="py-2.5">
                    <StatusBadge status={incident.status} />
                  </td>
                  <td className="py-2.5 text-slate-500">{new Date(incident.openedAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
