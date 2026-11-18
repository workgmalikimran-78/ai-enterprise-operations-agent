import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { OverviewPage } from './pages/OverviewPage'
import { LiveMonitoringPage } from './pages/LiveMonitoringPage'
import { IncidentsListPage } from './pages/IncidentsListPage'
import { IncidentDetailPage } from './pages/IncidentDetailPage'
import { RemediationCenterPage } from './pages/RemediationCenterPage'
import { AuditCenterPage } from './pages/AuditCenterPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route index element={<OverviewPage />} />
            <Route path="monitoring" element={<LiveMonitoringPage />} />
            <Route path="incidents" element={<IncidentsListPage />} />
            <Route path="incidents/:id" element={<IncidentDetailPage />} />
            <Route path="remediation" element={<RemediationCenterPage />} />
            <Route path="audit" element={<AuditCenterPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  )
}

export default App
