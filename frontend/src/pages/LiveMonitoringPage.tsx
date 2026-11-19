import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { api } from '../api/client'
import { SeverityBadge } from '../components/Badges'
import { Card } from '../components/StatCard'

export function LiveMonitoringPage() {
  const [service, setService] = useState('')
  const queryClient = useQueryClient()

  const [uploadService, setUploadService] = useState('')
  const [uploadEnvironment, setUploadEnvironment] = useState('')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

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

  const upload = useMutation({
    mutationFn: () => {
      if (!selectedFile) throw new Error('Choose a log file first')
      return api.logs.upload(selectedFile, uploadService.trim(), uploadEnvironment.trim())
    },
    onSuccess: () => {
      setSelectedFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      queryClient.invalidateQueries({ queryKey: ['logs'] })
      queryClient.invalidateQueries({ queryKey: ['anomalies'] })
    },
  })

  const canUpload = !!selectedFile && uploadService.trim().length > 0 && uploadEnvironment.trim().length > 0

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Live Monitoring</h1>
        <p className="text-sm text-slate-500 mt-1">Open anomalies and recent error events across applications</p>
      </div>

      <Card title="Upload Application Log File">
        <p className="text-sm text-slate-500 mb-3">
          Bulk-ingest a log file (one entry per line; multi-line stack traces are attached to the
          preceding ERROR/FATAL line automatically). Each entry goes through the same pipeline as a
          single log event, feeding anomaly detection immediately.
        </p>
        <div className="flex flex-wrap items-center gap-3">
          <input
            ref={fileInputRef}
            type="file"
            accept=".log,.txt,text/plain"
            onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
            className="text-sm text-slate-600 file:mr-3 file:py-1.5 file:px-3 file:rounded-md file:border-0 file:text-sm file:font-medium file:bg-slate-100 file:text-slate-700 hover:file:bg-slate-200"
          />
          <input
            value={uploadService}
            onChange={(e) => setUploadService(e.target.value)}
            placeholder="Service (e.g. order-service)"
            className="text-sm border border-slate-300 rounded-md px-3 py-1.5 w-56 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <input
            value={uploadEnvironment}
            onChange={(e) => setUploadEnvironment(e.target.value)}
            placeholder="Environment (e.g. production)"
            className="text-sm border border-slate-300 rounded-md px-3 py-1.5 w-56 focus:outline-none focus:ring-2 focus:ring-indigo-500"
          />
          <button
            onClick={() => upload.mutate()}
            disabled={!canUpload || upload.isPending}
            className="text-sm font-medium bg-indigo-600 text-white px-3 py-1.5 rounded-md hover:bg-indigo-700 disabled:opacity-50"
          >
            {upload.isPending ? 'Uploading…' : 'Upload'}
          </button>
        </div>
        {upload.isError && (
          <p className="text-sm text-red-600 mt-2">Upload failed: {(upload.error as Error).message}</p>
        )}
        {upload.data && (
          <div className="text-sm text-slate-600 mt-2">
            Parsed {upload.data.entriesParsed} entries · ingested {upload.data.entriesIngested}
            {upload.data.entriesFailed > 0 && (
              <span className="text-amber-700"> · {upload.data.entriesFailed} failed</span>
            )}
            {upload.data.errors.length > 0 && (
              <ul className="list-disc list-inside text-xs text-red-600 mt-1">
                {upload.data.errors.map((err, i) => (
                  <li key={i}>{err}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </Card>

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
