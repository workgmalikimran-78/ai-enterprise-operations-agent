import { NavLink, Outlet } from 'react-router-dom'

const navItems = [
  { to: '/', label: 'Overview', end: true },
  { to: '/monitoring', label: 'Live Monitoring' },
  { to: '/incidents', label: 'Incidents' },
  { to: '/remediation', label: 'Remediation Center' },
  { to: '/audit', label: 'Audit Center' },
]

export function Layout() {
  return (
    <div className="flex min-h-screen">
      <aside className="w-60 shrink-0 border-r border-slate-200 bg-white">
        <div className="px-5 py-5 border-b border-slate-200">
          <h1 className="text-base font-semibold text-slate-900">AI Operations Agent</h1>
          <p className="text-xs text-slate-500 mt-0.5">Enterprise AIOps</p>
        </div>
        <nav className="flex flex-col gap-0.5 p-3">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `rounded-md px-3 py-2 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-indigo-50 text-indigo-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>
      </aside>
      <main className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-7xl px-8 py-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
