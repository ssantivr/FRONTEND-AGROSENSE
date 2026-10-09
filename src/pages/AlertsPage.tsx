import { useState } from 'react';
import { AsyncBoundary, PageHeader, SeverityBadge } from '../components/common';
import type { DataSource } from '../data/dataSource';
import { alertTypeLabels, formatDateTime, formatNumber } from '../labels';
import { errorMessage, useAsync } from '../useAsync';

export function AlertsPage({ dataSource }: { dataSource: DataSource }) {
  const { data, loading, error, reload } = useAsync(
    async () => {
      const [alerts, crops] = await Promise.all([dataSource.getAlerts(), dataSource.getCrops()]);
      return { alerts, crops };
    },
    [dataSource],
  );
  const [onlyOpen, setOnlyOpen] = useState(true);
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function acknowledge(idAlert: number) {
    setPendingId(idAlert);
    setActionError(null);
    try {
      await dataSource.acknowledgeAlert(idAlert);
      reload();
    } catch (reason) {
      setActionError(errorMessage(reason));
    } finally {
      setPendingId(null);
    }
  }

  const alerts = (data?.alerts ?? [])
    .filter((alert) => !onlyOpen || !alert.acknowledged)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  return (
    <>
      <PageHeader title="Alertas" description="Avisos generados por los sensores y el sistema." />
      <AsyncBoundary loading={loading && !data} error={error} onRetry={reload}>
        <section className="card">
          <label className="checkbox">
            <input type="checkbox" checked={onlyOpen} onChange={(event) => setOnlyOpen(event.target.checked)} />
            Mostrar solo las alertas sin atender
          </label>
          {actionError && (
            <p className="form-error" role="alert">
              {actionError}
            </p>
          )}
          {alerts.length === 0 ? (
            <p className="empty">No hay alertas para mostrar.</p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Severidad</th>
                    <th>Alerta</th>
                    <th>Cultivo</th>
                    <th className="numeric">Valor detectado</th>
                    <th>Fecha</th>
                    <th>Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {alerts.map((alert) => (
                    <tr key={alert.idAlert}>
                      <td>
                        <SeverityBadge severity={alert.severity} />
                      </td>
                      <td>
                        <strong>{alertTypeLabels[alert.alertType]}</strong>
                        <p className="muted">{alert.message}</p>
                      </td>
                      <td>{data?.crops.find((crop) => crop.idCrop === alert.idCrop)?.name ?? '—'}</td>
                      <td className="numeric">{formatNumber(alert.detectedValue)}</td>
                      <td>{formatDateTime(alert.createdAt)}</td>
                      <td>
                        {alert.acknowledged ? (
                          <span className="muted">Atendida</span>
                        ) : (
                          <button
                            type="button"
                            className="button secondary"
                            disabled={pendingId === alert.idAlert}
                            onClick={() => acknowledge(alert.idAlert)}
                          >
                            Marcar como atendida
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </AsyncBoundary>
    </>
  );
}
