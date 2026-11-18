import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { SeverityBadge, StatusBadge } from '../components/Badges'
import { Card } from '../components/StatCard'

export function IncidentDetailPage() {
  const { id } = useParams<{ id: string }>()
  const queryClient = useQueryClient()
  if (!id) return null

  const incident = useQuery({ queryKey: ['incident', id], queryFn: () => api.incidents.get(id) })
  const analysis = useQuery({ queryKey: ['incident', id, 'analysis'], queryFn: () => api.incidents.analysis(id) })
  const recommendations = useQuery({
    queryKey: ['incident', id, 'recommendations'],
    queryFn: () => api.incidents.recommendations(id),
  })
  const agentExecutions = useQuery({
    queryKey: ['incident', id, 'agent-executions'],
    queryFn: () => api.incidents.agentExecutions(id),
  })
  const logs = useQuery({
    queryKey: ['incident', id, 'logs'],
    queryFn: () => (incident.data ? api.logs.search({ service: incident.data.applicationName }) : null),
    enabled: !!incident.data,
  })

  const invalidateAll = () => {
    queryClient.invalidateQueries({ queryKey: ['incident', id] })
  }

  const investigate = useMutation({
    mutationFn: () => api.incidents.investigate(id),
    onSuccess: invalidateAll,
  })
  const recommend = useMutation({
    mutationFn: () => api.incidents.recommend(id),
    onSuccess: invalidateAll,
  })

  if (incident.isLoading) return <p className="text-sm text-slate-500">Loading…</p>
  if (!incident.data) return <p className="text-sm text-red-600">Incident not found.</p>

  const inc = incident.data

  return (
    <div className="space-y-6">
      <div>
        <Link to="/incidents" className="text-sm text-indigo-700 hover:underline">
          ← Back to incidents
        </Link>
        <div className="flex items-center gap-3 mt-2">
          <h1 className="text-2xl font-semibold text-slate-900">{inc.title}</h1>
          <SeverityBadge severity={inc.severity} />
          <StatusBadge status={inc.status} />
        </div>
        <p className="text-sm text-slate-500 mt-1">
          {inc.applicationName} · opened {new Date(inc.openedAt).toLocaleString()}
          {inc.resolvedAt && ` · resolved ${new Date(inc.resolvedAt).toLocaleString()}`}
        </p>
      </div>

      <Card title="AI Root Cause Analysis">
        {!analysis.data && (
          <div className="flex items-center justify-between">
            <p className="text-sm text-slate-400">No analysis yet.</p>
            <button
              onClick={() => investigate.mutate()}
              disabled={investigate.isPending}
              className="text-sm font-medium bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            >
              {investigate.isPending ? 'Investigating…' : 'Run Investigation'}
            </button>
          </div>
        )}
        {investigate.isError && (
          <p className="text-sm text-red-600 mt-2">
            Investigation failed: {(investigate.error as Error).message}. (Is OPENAI_API_KEY configured?)
          </p>
        )}
        {analysis.data && (
          <div className="space-y-3">
            <div className="flex items-center gap-2">
              <span className="text-xs font-medium text-slate-400 uppercase">Confidence</span>
              <span className="text-sm font-semibold text-slate-800">
                {Math.round(analysis.data.confidence * 100)}%
              </span>
            </div>
            <p className="text-sm text-slate-700">{analysis.data.summary}</p>
            {analysis.data.details?.rootCauseHypothesis && (
              <p className="text-sm text-slate-600">
                <span className="font-medium">Hypothesis: </span>
                {analysis.data.details.rootCauseHypothesis}
              </p>
            )}
            {analysis.data.details?.confirmedFacts && analysis.data.details.confirmedFacts.length > 0 && (
              <div>
                <p className="text-xs font-medium text-slate-400 uppercase">Confirmed facts</p>
                <ul className="list-disc list-inside text-sm text-slate-600">
                  {analysis.data.details.confirmedFacts.map((f, i) => (
                    <li key={i}>{f}</li>
                  ))}
                </ul>
              </div>
            )}
            {analysis.data.details?.evidenceGaps && analysis.data.details.evidenceGaps.length > 0 && (
              <div>
                <p className="text-xs font-medium text-slate-400 uppercase">Evidence gaps</p>
                <ul className="list-disc list-inside text-sm text-slate-500">
                  {analysis.data.details.evidenceGaps.map((g, i) => (
                    <li key={i}>{g}</li>
                  ))}
                </ul>
              </div>
            )}
          </div>
        )}
      </Card>

      <Card title="Remediation Recommendations">
        <div className="flex items-center justify-between mb-3">
          <p className="text-sm text-slate-400">
            {recommendations.data?.length ? `${recommendations.data.length} proposed` : 'None proposed yet'}
          </p>
          <button
            onClick={() => recommend.mutate()}
            disabled={recommend.isPending || !analysis.data}
            className="text-sm font-medium bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 disabled:opacity-50"
            title={!analysis.data ? 'Run investigation first' : undefined}
          >
            {recommend.isPending ? 'Generating…' : 'Generate Recommendation'}
          </button>
        </div>
        {recommendations.data && recommendations.data.length > 0 && (
          <div className="space-y-3">
            {recommendations.data.map((rec) => (
              <div key={rec.id} className="border border-slate-200 rounded-md p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-slate-800">{rec.actionType.replace(/_/g, ' ')}</span>
                  <StatusBadge status={rec.status} />
                </div>
                <p className="text-sm text-slate-600 mt-1">{rec.description}</p>
                <div className="flex gap-4 mt-2 text-xs text-slate-500">
                  <span>Risk: {rec.riskLevel}</span>
                  <span>Approval: {rec.requiredApprovalLevel}</span>
                </div>
                {rec.rollbackStrategy && (
                  <p className="text-xs text-slate-400 mt-1">Rollback: {rec.rollbackStrategy}</p>
                )}
              </div>
            ))}
          </div>
        )}
        <Link to="/remediation" className="text-sm text-indigo-700 hover:underline mt-3 inline-block">
          Go to Remediation Center to approve/execute →
        </Link>
      </Card>

      <Card title="Agent Execution Trace">
        {agentExecutions.data && agentExecutions.data.length === 0 && (
          <p className="text-sm text-slate-400">No agent activity recorded.</p>
        )}
        {agentExecutions.data && agentExecutions.data.length > 0 && (
          <div className="divide-y divide-slate-100">
            {agentExecutions.data.map((e) => (
              <div key={e.id} className="py-2.5 flex items-center justify-between">
                <div>
                  <span className="text-sm font-medium text-slate-700">{e.agentType.replace(/_/g, ' ')}</span>
                  <span className="text-xs text-slate-400 ml-2">
                    {e.startedAt ? new Date(e.startedAt).toLocaleString() : ''}
                  </span>
                </div>
                <StatusBadge status={e.status} />
              </div>
            ))}
          </div>
        )}
      </Card>

      <Card title="Related Logs">
        {logs.data && logs.data.content.length === 0 && <p className="text-sm text-slate-400">No related logs.</p>}
        {logs.data && logs.data.content.length > 0 && (
          <div className="space-y-1.5 max-h-80 overflow-y-auto">
            {logs.data.content.map((log) => (
              <div key={log.id} className="text-sm border-b border-slate-100 pb-1.5">
                <span className="text-xs text-slate-400 mr-2">{new Date(log.timestamp).toLocaleTimeString()}</span>
                <span
                  className={`text-xs font-medium mr-2 ${log.level === 'ERROR' || log.level === 'FATAL' ? 'text-red-600' : 'text-slate-500'}`}
                >
                  {log.level}
                </span>
                <span className="text-slate-700">{log.message}</span>
                {log.traceId && <span className="text-xs text-slate-400 ml-2">trace: {log.traceId}</span>}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  )
}
