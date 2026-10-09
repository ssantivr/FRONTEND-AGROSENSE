import { useState, type FormEvent } from 'react';
import { AsyncBoundary, PageHeader } from '../components/common';
import type { DataSource } from '../data/dataSource';
import { formatDateTime, formatNumber, irrigationTypeLabels } from '../labels';
import { errorMessage, useAsync } from '../useAsync';

const MAX_DURATION_MIN = 240;

export function IrrigationPage({ dataSource }: { dataSource: DataSource }) {
  const { data, loading, error, reload } = useAsync(
    async () => {
      const [irrigations, crops] = await Promise.all([dataSource.getIrrigations(), dataSource.getCrops()]);
      return { irrigations, crops };
    },
    [dataSource],
  );
  const [cropId, setCropId] = useState('');
  const [duration, setDuration] = useState('30');
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const activeCrops = data?.crops.filter((crop) => crop.active) ?? [];
  const selectedCropId = cropId || String(activeCrops[0]?.idCrop ?? '');

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const minutes = Number(duration);
    if (!selectedCropId) {
      setFormError('Selecciona un cultivo.');
      return;
    }
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > MAX_DURATION_MIN) {
      setFormError(`La duración debe ser un número entero entre 1 y ${MAX_DURATION_MIN} minutos.`);
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      await dataSource.startIrrigation(Number(selectedCropId), minutes);
      reload();
    } catch (reason) {
      setFormError(errorMessage(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader title="Riego" description="Inicia un riego manual y consulta el historial." />
      <AsyncBoundary loading={loading && !data} error={error} onRetry={reload}>
        <section className="card">
          <h2>Iniciar riego manual</h2>
          <form className="inline-form" onSubmit={handleSubmit} noValidate>
            <label>
              Cultivo
              <select value={selectedCropId} onChange={(event) => setCropId(event.target.value)}>
                {activeCrops.map((crop) => (
                  <option key={crop.idCrop} value={crop.idCrop}>
                    {crop.name}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Duración (minutos)
              <input
                type="number"
                min={1}
                max={MAX_DURATION_MIN}
                value={duration}
                onChange={(event) => setDuration(event.target.value)}
              />
            </label>
            <button type="submit" className="button" disabled={submitting}>
              {submitting ? 'Iniciando…' : 'Iniciar riego'}
            </button>
          </form>
          {formError && (
            <p className="form-error" role="alert">
              {formError}
            </p>
          )}
        </section>

        <section className="card">
          <h2>Historial</h2>
          {data?.irrigations.length === 0 ? (
            <p className="empty">Aún no hay riegos registrados.</p>
          ) : (
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Cultivo</th>
                    <th>Inicio</th>
                    <th>Fin</th>
                    <th className="numeric">Duración (min)</th>
                    <th className="numeric">Agua (L)</th>
                    <th>Tipo</th>
                  </tr>
                </thead>
                <tbody>
                  {data?.irrigations.map((irrigation) => (
                    <tr key={irrigation.idIrrigation}>
                      <td>{data.crops.find((crop) => crop.idCrop === irrigation.idCrop)?.name ?? '—'}</td>
                      <td>{formatDateTime(irrigation.startedAt)}</td>
                      <td>{irrigation.endedAt ? formatDateTime(irrigation.endedAt) : 'En curso'}</td>
                      <td className="numeric">{formatNumber(irrigation.durationMin)}</td>
                      <td className="numeric">{formatNumber(irrigation.waterLiters)}</td>
                      <td>{irrigationTypeLabels[irrigation.type]}</td>
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
