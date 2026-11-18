import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { StatusBadge } from '../components/Badges'
import { Card } from '../components/StatCard'

export function AuditCenterPage() {
  const operationalAudits = useQuery({ queryKey: ['audit', 'operational'], queryFn: api.audit.operational })
  const agentExecutions = useQuery({ queryKey: ['audit', 'agent-executions'], queryFn: api.audit.agentExecutions })

  const failedExecutions = agentExecutions.data?.content.filter((e) => e.status === 'FAILED') ?? []

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Audit Center</h1>
        <p className="text-sm text-slate-500 mt-1">AI decisions, human approvals, and tool execution history</p>
      </div>

      {failedExecutions.length > 0 && (
        <Card title="Failed Agent Executions">
          <div className="space-y-2">
            {failedExecutions.map((e) => (
              <div key={e.id} className="text-sm border-l-2 border-red-400 pl-3 py-1">
                <span className="font-medium text-slate-700">{e.agentType.replace(/_/g, ' ')}</span>
                <span className="text-xs text-slate-400 ml-2">
                  {e.startedAt && new Date(e.startedAt).toLocaleString()}
                </span>
                {e.output?.error != null && (
                  <p className="text-xs text-red-600 mt-0.5">{String(e.output.error)}</p>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card title="Agent Execution History">
        {agentExecutions.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {agentExecutions.data && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-400 border-b border-slate-200">
                <th className="py-2 font-medium">Agent</th>
                <th className="py-2 font-medium">Status</th>
                <th className="py-2 font-medium">Started</th>
                <th className="py-2 font-medium">Completed</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {agentExecutions.data.content.map((e) => (
                <tr key={e.id}>
                  <td className="py-2 font-medium text-slate-700">{e.agentType.replace(/_/g, ' ')}</td>
                  <td className="py-2">
                    <StatusBadge status={e.status} />
                  </td>
                  <td className="py-2 text-slate-500">{e.startedAt ? new Date(e.startedAt).toLocaleString() : '—'}</td>
                  <td className="py-2 text-slate-500">
                    {e.completedAt ? new Date(e.completedAt).toLocaleString() : '—'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <Card title="Operational Audit Log">
        {operationalAudits.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {operationalAudits.data && operationalAudits.data.content.length === 0 && (
          <p className="text-sm text-slate-400">No audit records yet.</p>
        )}
        {operationalAudits.data && operationalAudits.data.content.length > 0 && (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-xs text-slate-400 border-b border-slate-200">
                <th className="py-2 font-medium">Action</th>
                <th className="py-2 font-medium">Actor</th>
                <th className="py-2 font-medium">Entity</th>
                <th className="py-2 font-medium">When</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {operationalAudits.data.content.map((a) => (
                <tr key={a.id}>
                  <td className="py-2 font-medium text-slate-700">{a.action.replace(/_/g, ' ')}</td>
                  <td className="py-2 text-slate-500">{a.actorEmail}</td>
                  <td className="py-2 text-slate-500">
                    {a.entityType} <span className="font-mono text-xs">{a.entityId.slice(0, 8)}</span>
                  </td>
                  <td className="py-2 text-slate-500">{new Date(a.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}
