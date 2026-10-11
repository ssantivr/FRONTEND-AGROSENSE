// Chart.js and Leaflet are served from WebJars as classic scripts and expose globals; only their types
// come from npm. L is undefined when its script failed to load, which the map code checks for.
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
