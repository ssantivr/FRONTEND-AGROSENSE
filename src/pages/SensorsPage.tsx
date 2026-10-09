import { AsyncBoundary, PageHeader, StateBadge } from '../components/common';
import type { DataSource } from '../data/dataSource';
import { formatDateTime, sensorTypeLabels } from '../labels';
import { useAsync } from '../useAsync';

export function SensorsPage({ dataSource }: { dataSource: DataSource }) {
  const { data, loading, error, reload } = useAsync(
    async () => {
      const [sensors, crops] = await Promise.all([dataSource.getSensors(), dataSource.getCrops()]);
      return { sensors, crops };
    },
    [dataSource],
  );

  return (
    <>
      <PageHeader title="Sensores" description="Sensores instalados y su última lectura recibida." />
      <AsyncBoundary loading={loading && !data} error={error} onRetry={reload}>
        <section className="card">
          {data?.sensors.length === 0 ? (
            <p className="empty">No hay sensores registrados.</p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Código</th>
                    <th>Tipo</th>
                    <th>Cultivo</th>
                    <th>Ubicación</th>
                    <th>Última lectura</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {data?.sensors.map((sensor) => (
                    <tr key={sensor.idSensor}>
                      <td>{sensor.sensorCode}</td>
                      <td>{sensorTypeLabels[sensor.sensorType]}</td>
                      <td>{data.crops.find((crop) => crop.idCrop === sensor.idCrop)?.name ?? '—'}</td>
                      <td>{sensor.location ?? '—'}</td>
                      <td>{formatDateTime(sensor.lastReadingAt)}</td>
                      <td>
                        <StateBadge active={sensor.active} onLabel="Activo" offLabel="Inactivo" />
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
