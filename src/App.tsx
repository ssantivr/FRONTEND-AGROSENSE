import { useState } from 'react';
import { dataSource as defaultDataSource, isDemoMode, type DataSource } from './data/dataSource';
import { AlertsPage } from './pages/AlertsPage';
import { DashboardPage } from './pages/DashboardPage';
import { EstatesPage } from './pages/EstatesPage';
import { IrrigationPage } from './pages/IrrigationPage';
import { LoginPage } from './pages/LoginPage';
import { SensorsPage } from './pages/SensorsPage';
import type { SessionUser } from './types';

const pages = [
  { id: 'dashboard', label: 'Panel', Component: DashboardPage },
  { id: 'estates', label: 'Fincas y cultivos', Component: EstatesPage },
  { id: 'sensors', label: 'Sensores', Component: SensorsPage },
  { id: 'alerts', label: 'Alertas', Component: AlertsPage },
  { id: 'irrigation', label: 'Riego', Component: IrrigationPage },
] as const;

type PageId = (typeof pages)[number]['id'];

interface Props {
  dataSource?: DataSource;
  demoMode?: boolean;
}

export function App({ dataSource = defaultDataSource, demoMode = isDemoMode }: Props) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [pageId, setPageId] = useState<PageId>('dashboard');

  if (!user) {
    return <LoginPage dataSource={dataSource} demoMode={demoMode} onLogin={setUser} />;
  }

  function handleLogout() {
    dataSource.logout();
    setUser(null);
    setPageId('dashboard');
  }

  const { Component } = pages.find((page) => page.id === pageId) ?? pages[0];

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">
          AgroSense
          {demoMode && <span className="demo-tag">Demostración</span>}
        </div>
        <nav aria-label="Secciones">
          {pages.map((page) => (
            <button
              key={page.id}
              type="button"
              className="nav-item"
              aria-current={page.id === pageId ? 'page' : undefined}
              onClick={() => setPageId(page.id)}
            >
              {page.label}
            </button>
          ))}
        </nav>
        <div className="session">
          <span>
            {user.name} {user.lastName}
          </span>
          <button type="button" className="link-button" onClick={handleLogout}>
            Cerrar sesión
          </button>
        </div>
      </aside>
      <main className="content">
        <Component dataSource={dataSource} />
      </main>
    </div>
  );
}
