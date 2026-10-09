import { useState } from 'react';
import { AsyncBoundary, PageHeader, SeverityBadge, StatCard } from '../components/common';
import { ReadingsChart } from '../components/ReadingsChart';
import type { DataSource } from '../data/dataSource';
import { alertTypeLabels, formatDateTime, sensorTypeLabels, thresholdsFor } from '../labels';
import { useAsync } from '../useAsync';

export function DashboardPage({ dataSource }: { dataSource: DataSource }) {
  const overview = useAsync(
    async () => {
      const [estates, crops, sensors, alerts] = await Promise.all([
        dataSource.getEstates(),
        dataSource.getCrops(),
        dataSource.getSensors(),
        dataSource.getAlerts(),
      ]);
      return { estates, crops, sensors, alerts };
    },
    [dataSource],
  );

  const [selectedSensorId, setSelectedSensorId] = useState<number | null>(null);
  const activeSensors = overview.data?.sensors.filter((sensor) => sensor.active) ?? [];
  const sensorId = selectedSensorId ?? activeSensors[0]?.idSensor ?? null;

  const readings = useAsync(
    () => (sensorId === null ? Promise.resolve([]) : dataSource.getReadings(sensorId)),
    [dataSource, sensorId],
  );

  return (
    <>
      <PageHeader title="Panel" description="Resumen del estado de tus fincas y cultivos." />
      <AsyncBoundary loading={overview.loading && !overview.data} error={overview.error} onRetry={overview.reload}>
        {overview.data &&
          (() => {
            const { estates, crops, sensors, alerts } = overview.data;
            const openAlerts = alerts.filter((alert) => !alert.acknowledged);
            const sensor = sensors.find((candidate) => candidate.idSensor === sensorId);
            const crop = crops.find((candidate) => candidate.idCrop === sensor?.idCrop);
            const cropName = (idCrop: number) =>
              crops.find((candidate) => candidate.idCrop === idCrop)?.name ?? '—';

            return (
              <>
                <section className="stat-grid" aria-label="Indicadores">
                  <StatCard label="Fincas" value={String(estates.length)} />
                  <StatCard label="Cultivos activos" value={String(crops.filter((item) => item.active).length)} />
                  <StatCard
                    label="Sensores activos"
                    value={String(activeSensors.length)}
                    hint={`de ${sensors.length} instalados`}
                  />
                  <StatCard label="Alertas sin atender" value={String(openAlerts.length)} />
                </section>

                <section className="card">
                  <div className="section-header">
                    <h2>Lecturas de las últimas 24 horas</h2>
                    {activeSensors.length > 0 && (
                      <label className="inline-field">
                        Sensor
                        <select
                          value={sensorId ?? ''}
                          onChange={(event) => setSelectedSensorId(Number(event.target.value))}
                        >
                          {activeSensors.map((item) => (
                            <option key={item.idSensor} value={item.idSensor}>
                              {item.sensorCode} · {sensorTypeLabels[item.sensorType]} · {cropName(item.idCrop)}
                            </option>
                          ))}
                        </select>
                      </label>
                    )}
                  </div>
                  {sensor ? (
                    <AsyncBoundary
                      loading={readings.loading && !readings.data}
                      error={readings.error}
                      onRetry={readings.reload}
                    >
                      <ReadingsChart
                        title={`${sensorTypeLabels[sensor.sensorType]} — ${cropName(sensor.idCrop)} (${sensor.sensorCode})`}
                        readings={readings.data ?? []}
                        thresholds={thresholdsFor(sensor.sensorType, crop)}
                      />
                    </AsyncBoundary>
                  ) : (
                    <p className="empty">No hay sensores activos.</p>
                  )}
                </section>

                <section className="card">
                  <h2>Alertas sin atender</h2>
                  {openAlerts.length === 0 ? (
                    <p className="empty">No hay alertas pendientes.</p>
                  ) : (
                    <ul className="alert-list">
                      {openAlerts.map((alert) => (
                        <li key={alert.idAlert}>
                          <SeverityBadge severity={alert.severity} />
                          <div>
                            <strong>
                              {alertTypeLabels[alert.alertType]} · {cropName(alert.idCrop)}
                            </strong>
                            <p className="muted">{alert.message}</p>
                          </div>
                          <time className="muted">{formatDateTime(alert.createdAt)}</time>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              </>
            );
          })()}
      </AsyncBoundary>
    </>
  );
}
