import { AsyncBoundary, PageHeader, StateBadge } from '../components/common';
import type { DataSource } from '../data/dataSource';
import { cropStageLabels, formatNumber } from '../labels';
import { useAsync } from '../useAsync';

const dateFormat = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeZone: 'UTC' });

export function EstatesPage({ dataSource }: { dataSource: DataSource }) {
  const { data, loading, error, reload } = useAsync(
    async () => {
      const [estates, crops] = await Promise.all([dataSource.getEstates(), dataSource.getCrops()]);
      return { estates, crops };
    },
    [dataSource],
  );

  return (
    <>
      <PageHeader title="Fincas y cultivos" description="Tus fincas y los cultivos sembrados en cada una." />
      <AsyncBoundary loading={loading && !data} error={error} onRetry={reload}>
        {data?.estates.length === 0 && <p className="empty">Aún no tienes fincas registradas.</p>}
        {data?.estates.map((estate) => {
          const crops = data.crops.filter((crop) => crop.idEstate === estate.idEstate);
          return (
            <section className="card" key={estate.idEstate}>
              <div className="section-header">
                <h2>{estate.name}</h2>
                <span className="muted">
                  {estate.location ?? 'Sin ubicación'}
                  {estate.areaHa !== null && ` · ${formatNumber(estate.areaHa)} ha`}
                </span>
              </div>
              {crops.length === 0 ? (
                <p className="empty">Esta finca no tiene cultivos.</p>
              ) : (
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>Cultivo</th>
                        <th>Variedad</th>
                        <th>Siembra</th>
                        <th>Etapa</th>
                        <th>Humedad (%)</th>
                        <th>Temperatura (°C)</th>
                        <th>pH</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {crops.map((crop) => (
                        <tr key={crop.idCrop}>
                          <td>{crop.name}</td>
                          <td>{crop.variety ?? '—'}</td>
                          <td>{crop.sowingDate ? dateFormat.format(new Date(crop.sowingDate)) : '—'}</td>
                          <td>{cropStageLabels[crop.stage]}</td>
                          <td>
                            {formatNumber(crop.humidityMin)} – {formatNumber(crop.humidityMax)}
                          </td>
                          <td>
                            {formatNumber(crop.tempMin)} – {formatNumber(crop.tempMax)}
                          </td>
                          <td>
                            {formatNumber(crop.phMin)} – {formatNumber(crop.phMax)}
                          </td>
                          <td>
                            <StateBadge active={crop.active} onLabel="Activo" offLabel="Inactivo" />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          );
        })}
      </AsyncBoundary>
    </>
  );
}
