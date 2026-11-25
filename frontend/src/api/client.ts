import axios from 'axios'
import type {
  AgentExecution,
  Anomaly,
  ApprovalRequest,
  DashboardMetrics,
  DashboardOverview,
  DataCatalogEntry,
  DataQualityCheck,
  EtlRunLog,
  FactIncidentDaily,
  FactLogVolumeHourly,
  FactRemediationDaily,
  Incident,
  LogEvent,
  LogFileUploadResult,
  OperationalAudit,
  PagedResponse,
  RcaAnalysis,
  RemediationExecution,
  RemediationRecommendation,
} from './types'

const http = axios.create({ baseURL: '/api/v1' })

export const api = {
  dashboard: {
    overview: () => http.get<DashboardOverview>('/dashboard/overview').then((r) => r.data),
    metrics: () => http.get<DashboardMetrics>('/dashboard/metrics').then((r) => r.data),
  },
  incidents: {
    list: (status?: string) =>
      http
        .get<PagedResponse<Incident>>('/incidents', { params: { status, size: 50 } })
        .then((r) => r.data),
    get: (id: string) => http.get<Incident>(`/incidents/${id}`).then((r) => r.data),
    investigate: (id: string) => http.post<RcaAnalysis>(`/incidents/${id}/investigate`).then((r) => r.data),
    analysis: (id: string) =>
      http
        .get<RcaAnalysis>(`/incidents/${id}/analysis`)
        .then((r) => r.data)
        .catch(() => null),
    recommend: (id: string) =>
      http.post<RemediationRecommendation>(`/incidents/${id}/recommendations`).then((r) => r.data),
    recommendations: (id: string) =>
      http.get<RemediationRecommendation[]>(`/incidents/${id}/recommendations`).then((r) => r.data),
    agentExecutions: (id: string) =>
      http
        .get<PagedResponse<AgentExecution>>(`/incidents/${id}/agent-executions`, { params: { size: 50 } })
        .then((r) => r.data.content),
  },
  logs: {
    search: (params: { service?: string; level?: string; q?: string }) =>
      http.get<PagedResponse<LogEvent>>('/logs/search', { params: { ...params, size: 50 } }).then((r) => r.data),
    upload: (file: File, service: string, environment: string) => {
      const formData = new FormData()
      formData.append('file', file)
      formData.append('service', service)
      formData.append('environment', environment)
      return http
        .post<LogFileUploadResult>('/logs/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        .then((r) => r.data)
    },
  },
  anomalies: {
    list: (status?: string) =>
      http.get<PagedResponse<Anomaly>>('/anomalies', { params: { status, size: 20 } }).then((r) => r.data),
  },
  approvals: {
    list: (status?: string) =>
      http
        .get<PagedResponse<ApprovalRequest>>('/approvals', { params: { status, size: 50 } })
        .then((r) => r.data),
    approve: (id: string, approverEmail: string) =>
      http.post<ApprovalRequest>(`/approvals/${id}/approve`, { approverEmail }).then((r) => r.data),
    reject: (id: string, approverEmail: string, reason: string) =>
      http.post<ApprovalRequest>(`/approvals/${id}/reject`, { approverEmail, reason }).then((r) => r.data),
  },
  remediations: {
    execute: (id: string) => http.post<RemediationExecution>(`/remediations/${id}/execute`).then((r) => r.data),
    status: (id: string) =>
      http
        .get<RemediationExecution>(`/remediations/${id}/status`)
        .then((r) => r.data)
        .catch(() => null),
  },
  audit: {
    operational: () =>
      http.get<PagedResponse<OperationalAudit>>('/audit', { params: { size: 50 } }).then((r) => r.data),
    agentExecutions: () =>
      http
        .get<PagedResponse<AgentExecution>>('/audit/agent-executions', { params: { size: 50 } })
        .then((r) => r.data),
  },
  warehouse: {
    catalog: () => http.get<DataCatalogEntry[]>('/warehouse/catalog').then((r) => r.data),
    qualityChecks: () =>
      http
        .get<PagedResponse<DataQualityCheck>>('/warehouse/quality-checks', { params: { size: 20 } })
        .then((r) => r.data),
    etlRuns: () =>
      http.get<PagedResponse<EtlRunLog>>('/warehouse/etl-runs', { params: { size: 10 } }).then((r) => r.data),
    runEtlNow: () => http.post<EtlRunLog>('/warehouse/etl/run').then((r) => r.data),
    incidentsDaily: () =>
      http.get<FactIncidentDaily[]>('/warehouse/metrics/incidents-daily').then((r) => r.data),
    remediationDaily: () =>
      http.get<FactRemediationDaily[]>('/warehouse/metrics/remediation-daily').then((r) => r.data),
    logVolumeHourly: () =>
      http.get<FactLogVolumeHourly[]>('/warehouse/metrics/log-volume-hourly').then((r) => r.data),
  },
}
