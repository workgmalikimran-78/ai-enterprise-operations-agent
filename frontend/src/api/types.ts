export interface PagedResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface DashboardOverview {
  totalIncidents: number
  criticalIncidents: number
  activeAnomalies: number
  resolvedIncidents: number
  mttdSeconds: number | null
  mttrSeconds: number | null
  aiInvestigations: number
  aiInvestigationFailures: number
}

export interface DashboardMetrics {
  incidentsBySeverity: Record<string, number>
  incidentsByStatus: Record<string, number>
  anomaliesByDetectionType: Record<string, number>
  agentExecutionsByType: Record<string, number>
}

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export type IncidentStatus = 'OPEN' | 'INVESTIGATING' | 'MITIGATED' | 'RESOLVED' | 'CLOSED'

export interface Incident {
  id: string
  title: string
  status: IncidentStatus
  severity: Severity
  applicationId: string
  applicationName: string
  openedAt: string
  resolvedAt: string | null
}

export interface LogEvent {
  id: string
  timestamp: string
  service: string
  environment: string
  level: string
  traceId: string | null
  message: string
  exceptionType: string | null
  exceptionMessage: string | null
  createdAt: string
}

export interface RcaAnalysis {
  id: string
  incidentId: string
  summary: string
  confidence: number
  llmProvider: string
  details: {
    rootCauseHypothesis?: string
    confirmedFacts?: string[]
    hypotheses?: string[]
    evidenceGaps?: string[]
  } | null
  createdAt: string
}

export interface RemediationRecommendation {
  id: string
  incidentId: string
  actionType: string
  description: string
  riskLevel: string
  requiredApprovalLevel: 'NONE' | 'LOW' | 'HIGH'
  expectedOutcome: string | null
  businessImpact: string | null
  rollbackStrategy: string | null
  status: string
  createdAt: string
}

export interface ApprovalRequest {
  id: string
  remediationRecommendationId: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'EXPIRED' | 'CANCELLED'
  decidedByEmail: string | null
  decidedAt: string | null
  expiresAt: string | null
  rejectionReason: string | null
  createdAt: string
}

export interface RemediationExecution {
  id: string
  remediationRecommendationId: string
  status: string
  k8sJobRef: string | null
  resultLog: string | null
  startedAt: string | null
  completedAt: string | null
  verifiedAt: string | null
}

export interface OperationalAudit {
  id: string
  actorEmail: string
  action: string
  entityType: string
  entityId: string
  details: Record<string, unknown> | null
  createdAt: string
}

export interface Anomaly {
  id: string
  applicationId: string
  applicationName: string
  detectionType: string
  status: string
  severity: Severity
  description: string
  windowStart: string
  windowEnd: string
  createdAt: string
}

export interface AgentExecution {
  id: string
  incidentId: string | null
  agentType: string
  status: string
  input: Record<string, unknown> | null
  output: Record<string, unknown> | null
  startedAt: string | null
  completedAt: string | null
}
