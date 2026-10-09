import { useState, type PointerEvent } from 'react';
import { formatDateTime, formatNumber, formatTime } from '../labels';
import type { SensorReading } from '../types';

interface Props {
  title: string;
  readings: SensorReading[];
  thresholds: { min: number; max: number } | null;
}

const WIDTH = 720;
const HEIGHT = 260;
const MARGIN = { top: 16, right: 56, bottom: 28, left: 44 };
const PLOT_WIDTH = WIDTH - MARGIN.left - MARGIN.right;
const PLOT_HEIGHT = HEIGHT - MARGIN.top - MARGIN.bottom;
const Y_TICKS = 4;
const X_TICK_EVERY = 6;

/** Rounds a raw tick interval up to 1, 2, 2.5 or 5 times a power of ten. */
function niceStep(rawStep: number): number {
  const magnitude = 10 ** Math.floor(Math.log10(rawStep));
  const candidate = [1, 2, 2.5, 5, 10].find((factor) => factor * magnitude >= rawStep) ?? 10;
  return candidate * magnitude;
}

export function ReadingsChart({ title, readings, thresholds }: Props) {
  const [hovered, setHovered] = useState<number | null>(null);
  const [showTable, setShowTable] = useState(false);

  if (readings.length === 0) {
    return (
      <figure className="chart">
        <figcaption className="chart-title">{title}</figcaption>
        <p className="empty">Este sensor no tiene lecturas en las últimas 24 horas.</p>
      </figure>
    );
  }

  const unit = readings[0].unit;
  const values = readings.map((reading) => reading.value);
  const bounds = thresholds ? [...values, thresholds.min, thresholds.max] : values;
  const rawMin = Math.min(...bounds);
  const rawMax = Math.max(...bounds);
  const step = niceStep((rawMax - rawMin || 1) / Y_TICKS);
  const yMin = Math.floor(rawMin / step) * step;
  const yMax = Math.max(Math.ceil(rawMax / step) * step, yMin + step);

  const xOf = (index: number) =>
    MARGIN.left + (readings.length === 1 ? PLOT_WIDTH / 2 : (index / (readings.length - 1)) * PLOT_WIDTH);
  const yOf = (value: number) => MARGIN.top + (1 - (value - yMin) / (yMax - yMin)) * PLOT_HEIGHT;

  const path = readings
    .map((reading, index) => `${index === 0 ? 'M' : 'L'}${xOf(index).toFixed(1)},${yOf(reading.value).toFixed(1)}`)
    .join(' ');

  const yTicks = Array.from({ length: Math.round((yMax - yMin) / step) + 1 }, (_, i) => yMin + step * i);

  function handlePointerMove(event: PointerEvent<SVGSVGElement>) {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width === 0) return;
    const x = ((event.clientX - rect.left) / rect.width) * WIDTH;
    const ratio = (x - MARGIN.left) / PLOT_WIDTH;
    const index = Math.round(ratio * (readings.length - 1));
    setHovered(Math.min(readings.length - 1, Math.max(0, index)));
  }

  const active = hovered === null ? null : readings[hovered];

  return (
    <figure className="chart">
      <div className="chart-header">
        <figcaption className="chart-title">{title}</figcaption>
        <button type="button" className="link-button" onClick={() => setShowTable((value) => !value)}>
          {showTable ? 'Ver gráfica' : 'Ver tabla'}
        </button>
      </div>

      {showTable ? (
        <div className="table-scroll chart-table">
          <table>
            <thead>
              <tr>
                <th>Hora</th>
                <th className="numeric">Valor ({unit})</th>
              </tr>
            </thead>
            <tbody>
              {readings.map((reading) => (
                <tr key={reading.idReading}>
                  <td>{formatDateTime(reading.recordedAt)}</td>
                  <td className="numeric">{formatNumber(reading.value)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="chart-plot">
          <svg
            viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
            role="img"
            aria-label={`${title}. Usa «Ver tabla» para consultar los valores.`}
            onPointerMove={handlePointerMove}
            onPointerLeave={() => setHovered(null)}
          >
            {yTicks.map((tick) => (
              <g key={tick}>
                <line className="chart-grid" x1={MARGIN.left} x2={MARGIN.left + PLOT_WIDTH} y1={yOf(tick)} y2={yOf(tick)} />
                <text className="chart-tick" x={MARGIN.left - 8} y={yOf(tick)} textAnchor="end" dominantBaseline="middle">
                  {formatNumber(tick)}
                </text>
              </g>
            ))}

            {readings.map((reading, index) =>
              index % X_TICK_EVERY === 0 ? (
                <text key={reading.idReading} className="chart-tick" x={xOf(index)} y={HEIGHT - 8} textAnchor="middle">
                  {formatTime(reading.recordedAt)}
                </text>
              ) : null,
            )}

            {thresholds &&
              ([
                ['Mín', thresholds.min],
                ['Máx', thresholds.max],
              ] as const).map(([label, value]) => (
                <g key={label}>
                  <line
                    className="chart-threshold"
                    x1={MARGIN.left}
                    x2={MARGIN.left + PLOT_WIDTH}
                    y1={yOf(value)}
                    y2={yOf(value)}
                  />
                  <text className="chart-tick" x={MARGIN.left + PLOT_WIDTH + 6} y={yOf(value)} dominantBaseline="middle">
                    {label} {formatNumber(value)}
                  </text>
                </g>
              ))}

            <path className="chart-line" d={path} />

            {hovered !== null && active && (
              <g>
                <line
                  className="chart-crosshair"
                  x1={xOf(hovered)}
                  x2={xOf(hovered)}
                  y1={MARGIN.top}
                  y2={MARGIN.top + PLOT_HEIGHT}
                />
                <circle className="chart-marker" cx={xOf(hovered)} cy={yOf(active.value)} r={5} />
              </g>
            )}
          </svg>

          {hovered !== null && active && (
            <div
              className="chart-tooltip"
              style={{
                left: `${(xOf(hovered) / WIDTH) * 100}%`,
                top: `${(yOf(active.value) / HEIGHT) * 100}%`,
              }}
            >
              <strong>
                {formatNumber(active.value)} {unit}
              </strong>
              <span>{formatDateTime(active.recordedAt)}</span>
            </div>
          )}
        </div>
      )}
    </figure>
  );
}
