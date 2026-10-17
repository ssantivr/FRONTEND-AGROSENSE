import type { Chart as ChartClass, ChartType } from 'chart.js';
import type * as Leaflet from 'leaflet';

export interface ThresholdLine {
  value: number;
  label: string;
}

declare global {
  const Chart: typeof ChartClass;
  const L: typeof Leaflet | undefined;
}

declare module 'chart.js' {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  interface PluginOptionsByType<TType extends ChartType> {
    thresholds?: { lines: ThresholdLine[] };
  }
}
