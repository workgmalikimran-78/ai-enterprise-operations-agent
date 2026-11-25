const severityColors: Record<string, string> = {
  LOW: 'bg-slate-100 text-slate-700',
  MEDIUM: 'bg-amber-100 text-amber-800',
  HIGH: 'bg-orange-100 text-orange-800',
  CRITICAL: 'bg-red-100 text-red-800',
}

const statusColors: Record<string, string> = {
  OPEN: 'bg-red-100 text-red-800',
  INVESTIGATING: 'bg-amber-100 text-amber-800',
  MITIGATED: 'bg-blue-100 text-blue-800',
  RESOLVED: 'bg-emerald-100 text-emerald-800',
  CLOSED: 'bg-slate-100 text-slate-600',
  PENDING: 'bg-amber-100 text-amber-800',
  APPROVED: 'bg-emerald-100 text-emerald-800',
  REJECTED: 'bg-red-100 text-red-800',
  EXPIRED: 'bg-slate-100 text-slate-600',
  CANCELLED: 'bg-slate-100 text-slate-600',
  SUCCEEDED: 'bg-emerald-100 text-emerald-800',
  FAILED: 'bg-red-100 text-red-800',
  RUNNING: 'bg-blue-100 text-blue-800',
  PROPOSED: 'bg-slate-100 text-slate-700',
  APPROVAL_REQUESTED: 'bg-amber-100 text-amber-800',
  EXECUTED: 'bg-emerald-100 text-emerald-800',
  PASS: 'bg-emerald-100 text-emerald-800',
  FAIL: 'bg-red-100 text-red-800',
  WARN: 'bg-amber-100 text-amber-800',
}

const classificationColors: Record<string, string> = {
  PUBLIC: 'bg-slate-100 text-slate-600',
  INTERNAL: 'bg-blue-100 text-blue-800',
  CONFIDENTIAL: 'bg-red-100 text-red-800',
}

function Badge({ label, colorClass }: { label: string; colorClass: string }) {
  return (
    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${colorClass}`}>
      {label}
    </span>
  )
}

export function SeverityBadge({ severity }: { severity: string }) {
  return <Badge label={severity} colorClass={severityColors[severity] ?? 'bg-slate-100 text-slate-700'} />
}

export function StatusBadge({ status }: { status: string }) {
  return <Badge label={status.replace(/_/g, ' ')} colorClass={statusColors[status] ?? 'bg-slate-100 text-slate-700'} />
}

export function ClassificationBadge({ classification }: { classification: string }) {
  return (
    <Badge
      label={classification}
      colorClass={classificationColors[classification] ?? 'bg-slate-100 text-slate-700'}
    />
  )
}
