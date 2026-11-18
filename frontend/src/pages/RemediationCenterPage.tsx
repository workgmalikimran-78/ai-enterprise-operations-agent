import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import { StatusBadge } from '../components/Badges'
import { Card } from '../components/StatCard'
import type { ApprovalRequest } from '../api/types'

function ApprovalRow({ approval }: { approval: ApprovalRequest }) {
  const queryClient = useQueryClient()
  const [email, setEmail] = useState('ops@example.com')
  const [reason, setReason] = useState('')

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['approvals'] })

  const approve = useMutation({
    mutationFn: () => api.approvals.approve(approval.id, email),
    onSuccess: invalidate,
  })
  const reject = useMutation({
    mutationFn: () => api.approvals.reject(approval.id, email, reason || 'Rejected via dashboard'),
    onSuccess: invalidate,
  })
  const execute = useMutation({
    mutationFn: () => api.remediations.execute(approval.remediationRecommendationId),
  })

  const isPending = approval.status === 'PENDING'
  const isApproved = approval.status === 'APPROVED'

  return (
    <div className="border border-slate-200 rounded-md p-4 space-y-3">
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-mono text-slate-500">Recommendation {approval.remediationRecommendationId}</p>
          <p className="text-xs text-slate-400 mt-0.5">Requested {new Date(approval.createdAt).toLocaleString()}</p>
          {approval.expiresAt && (
            <p className="text-xs text-slate-400">Expires {new Date(approval.expiresAt).toLocaleString()}</p>
          )}
        </div>
        <StatusBadge status={approval.status} />
      </div>

      {approval.decidedByEmail && (
        <p className="text-xs text-slate-500">
          Decided by {approval.decidedByEmail} at {approval.decidedAt && new Date(approval.decidedAt).toLocaleString()}
        </p>
      )}
      {approval.rejectionReason && <p className="text-xs text-red-600">Reason: {approval.rejectionReason}</p>}

      {isPending && (
        <div className="flex flex-wrap items-center gap-2">
          <input
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="approver@email.com"
            className="text-sm border border-slate-300 rounded-md px-2 py-1 w-48"
          />
          <input
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Rejection reason (optional)"
            className="text-sm border border-slate-300 rounded-md px-2 py-1 flex-1 min-w-40"
          />
          <button
            onClick={() => approve.mutate()}
            disabled={approve.isPending}
            className="text-sm font-medium bg-emerald-600 text-white px-3 py-1.5 rounded-md hover:bg-emerald-700 disabled:opacity-50"
          >
            Approve
          </button>
          <button
            onClick={() => reject.mutate()}
            disabled={reject.isPending}
            className="text-sm font-medium bg-red-600 text-white px-3 py-1.5 rounded-md hover:bg-red-700 disabled:opacity-50"
          >
            Reject
          </button>
        </div>
      )}

      {isApproved && (
        <div className="flex items-center gap-2">
          <button
            onClick={() => execute.mutate()}
            disabled={execute.isPending}
            className="text-sm font-medium bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 disabled:opacity-50"
          >
            {execute.isPending ? 'Executing…' : 'Execute'}
          </button>
          {execute.isSuccess && (
            <span className="text-xs text-emerald-700">
              {execute.data.status} — {execute.data.resultLog?.slice(0, 80)}
            </span>
          )}
          {execute.isError && <span className="text-xs text-red-600">{(execute.error as Error).message}</span>}
        </div>
      )}
    </div>
  )
}

export function RemediationCenterPage() {
  const [status, setStatus] = useState('PENDING')
  const approvals = useQuery({
    queryKey: ['approvals', status],
    queryFn: () => api.approvals.list(status || undefined),
    refetchInterval: 10000,
  })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Remediation Center</h1>
          <p className="text-sm text-slate-500 mt-1">Human-in-the-loop approval for AI-proposed remediations</p>
        </div>
        <select
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          className="text-sm border border-slate-300 rounded-md px-3 py-1.5"
        >
          {['PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', ''].map((s) => (
            <option key={s} value={s}>
              {s === '' ? 'All' : s}
            </option>
          ))}
        </select>
      </div>

      <Card>
        {approvals.isLoading && <p className="text-sm text-slate-500">Loading…</p>}
        {approvals.data && approvals.data.content.length === 0 && (
          <p className="text-sm text-slate-400">No approval requests.</p>
        )}
        <div className="space-y-3">
          {approvals.data?.content.map((approval) => (
            <ApprovalRow key={approval.id} approval={approval} />
          ))}
        </div>
      </Card>
    </div>
  )
}
