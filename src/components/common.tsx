import type { ReactNode } from 'react';
import { severityLabels } from '../labels';
import type { Severity } from '../types';

export function PageHeader({ title, description }: { title: string; description: string }) {
  return (
    <header className="page-header">
      <h1>{title}</h1>
      <p>{description}</p>
    </header>
  );
}

export function StatCard({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="card stat-card">
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  );
}

/** Shows loading and error states for a request, and its children once data is available. */
export function AsyncBoundary({
  loading,
  error,
  onRetry,
  children,
}: {
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  children: ReactNode;
}) {
  if (error) {
    return (
      <div className="card notice" role="alert">
        <p>{error}</p>
        <button type="button" className="button secondary" onClick={onRetry}>
          Reintentar
        </button>
      </div>
    );
  }
  if (loading) {
    return <p className="empty">Cargando…</p>;
  }
  return <>{children}</>;
}

const severityIcons: Record<Severity, string> = {
  LOW: '●',
  MEDIUM: '▲',
  HIGH: '◆',
  VERY_HIGH: '■',
};

/** Severity is conveyed by icon shape and label; colour is only a reinforcement. */
export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <span className="badge">
      <span className={`badge-icon severity-${severity.toLowerCase()}`} aria-hidden="true">
        {severityIcons[severity]}
      </span>
      {severityLabels[severity]}
    </span>
  );
}

export function StateBadge({ active, onLabel, offLabel }: { active: boolean; onLabel: string; offLabel: string }) {
  return (
    <span className="badge">
      <span className={`badge-icon ${active ? 'state-on' : 'state-off'}`} aria-hidden="true">
        {active ? '●' : '○'}
      </span>
      {active ? onLabel : offLabel}
    </span>
  );
}
