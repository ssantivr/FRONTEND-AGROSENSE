import type { AlertType, Crop, CropStage, IrrigationType, SensorType, Severity } from './types';

export const cropStageLabels: Record<CropStage, string> = {
  GERMINATION: 'Germinación',
  GROWTH: 'Crecimiento',
  FLOWERING: 'Floración',
  FRUITING: 'Fructificación',
  RIPENING: 'Maduración',
  HARVEST: 'Cosecha',
};

export const sensorTypeLabels: Record<SensorType, string> = {
  SOIL_MOISTURE: 'Humedad del suelo',
  AIR_TEMPERATURE: 'Temperatura del aire',
  SOIL_TEMPERATURE: 'Temperatura del suelo',
  PH: 'pH',
  CONDUCTIVITY: 'Conductividad',
  LIGHT: 'Luz',
  RAIN_GAUGE: 'Pluviómetro',
  RELATIVE_HUMIDITY: 'Humedad relativa',
};

export const alertTypeLabels: Record<AlertType, string> = {
  LOW_HUMIDITY: 'Humedad baja',
  HIGH_HUMIDITY: 'Humedad alta',
  HIGH_TEMPERATURE: 'Temperatura alta',
  LOW_TEMPERATURE: 'Temperatura baja',
  PH_OUT_OF_RANGE: 'pH fuera de rango',
  HIGH_CONDUCTIVITY: 'Conductividad alta',
  PEST_DETECTED: 'Plaga detectada',
  RECOMMENDED_WATERING: 'Riego recomendado',
  WATER_STRESS: 'Estrés hídrico',
};

export const severityLabels: Record<Severity, string> = {
  LOW: 'Baja',
  MEDIUM: 'Media',
  HIGH: 'Alta',
  VERY_HIGH: 'Muy alta',
};

export const irrigationTypeLabels: Record<IrrigationType, string> = {
  AUTOMATIC: 'Automático',
  MANUAL: 'Manual',
};

const dateTimeFormat = new Intl.DateTimeFormat('es-CO', {
  day: '2-digit',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
});

const timeFormat = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' });

const numberFormat = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 });

export function formatDateTime(iso: string | null): string {
  return iso ? dateTimeFormat.format(new Date(iso)) : '—';
}

export function formatTime(iso: string): string {
  return timeFormat.format(new Date(iso));
}

export function formatNumber(value: number | null): string {
  return value === null ? '—' : numberFormat.format(value);
}

/** Returns the crop's acceptable range for the magnitude a sensor measures, if it has one. */
export function thresholdsFor(
  sensorType: SensorType,
  crop: Crop | undefined,
): { min: number; max: number } | null {
  if (!crop) return null;
  switch (sensorType) {
    case 'SOIL_MOISTURE':
    case 'RELATIVE_HUMIDITY':
      return { min: crop.humidityMin, max: crop.humidityMax };
    case 'AIR_TEMPERATURE':
    case 'SOIL_TEMPERATURE':
      return { min: crop.tempMin, max: crop.tempMax };
    case 'PH':
      return { min: crop.phMin, max: crop.phMax };
    default:
      return null;
  }
}
