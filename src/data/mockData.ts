import type { Alert, Crop, Estate, Irrigation, Sensor, SensorReading, SessionUser } from '../types';

export const DEMO_EMAIL = 'demo@agrosense.co';
export const DEMO_PASSWORD = 'agrosense';

const HOUR_MS = 60 * 60 * 1000;

function hoursAgo(hours: number): string {
  return new Date(Date.now() - hours * HOUR_MS).toISOString();
}

export const demoUser: SessionUser = {
  idUser: 1,
  name: 'Usuario',
  lastName: 'Demo',
  email: DEMO_EMAIL,
  role: 'farmer',
};

export const estates: Estate[] = [
  { idEstate: 1, name: 'Finca La Esperanza', location: 'Pasto, Nariño', areaHa: 12.5 },
  { idEstate: 2, name: 'Finca El Mirador', location: 'La Unión, Nariño', areaHa: 6 },
];

const defaultRanges = {
  humidityMin: 40,
  humidityMax: 80,
  tempMin: 15,
  tempMax: 35,
  phMin: 5.5,
  phMax: 7,
};

export const crops: Crop[] = [
  {
    ...defaultRanges,
    idCrop: 1,
    idEstate: 1,
    name: 'Café',
    variety: 'Castillo',
    sowingDate: '2025-03-14',
    stage: 'FLOWERING',
    active: true,
  },
  {
    ...defaultRanges,
    idCrop: 2,
    idEstate: 1,
    name: 'Plátano',
    variety: 'Hartón',
    sowingDate: '2025-11-02',
    stage: 'GROWTH',
    active: true,
  },
  {
    ...defaultRanges,
    idCrop: 3,
    idEstate: 2,
    name: 'Papa',
    variety: 'Pastusa',
    sowingDate: '2026-07-20',
    stage: 'GERMINATION',
    active: true,
  },
];

export const sensors: Sensor[] = [
  {
    idSensor: 1,
    idCrop: 1,
    sensorCode: 'AS-001',
    sensorType: 'SOIL_MOISTURE',
    location: 'Lote norte',
    active: true,
    lastReadingAt: hoursAgo(0),
  },
  {
    idSensor: 2,
    idCrop: 1,
    sensorCode: 'AS-002',
    sensorType: 'AIR_TEMPERATURE',
    location: 'Lote norte',
    active: true,
    lastReadingAt: hoursAgo(0),
  },
  {
    idSensor: 3,
    idCrop: 1,
    sensorCode: 'AS-003',
    sensorType: 'PH',
    location: 'Lote sur',
    active: true,
    lastReadingAt: hoursAgo(0),
  },
  {
    idSensor: 4,
    idCrop: 2,
    sensorCode: 'AS-004',
    sensorType: 'SOIL_MOISTURE',
    location: 'Platanera',
    active: true,
    lastReadingAt: hoursAgo(0),
  },
  {
    idSensor: 5,
    idCrop: 3,
    sensorCode: 'AS-005',
    sensorType: 'LIGHT',
    location: 'Parcela 1',
    active: false,
    lastReadingAt: hoursAgo(72),
  },
];

interface ReadingProfile {
  unit: string;
  base: number;
  amplitude: number;
  noise: number;
}

const profiles: Record<number, ReadingProfile> = {
  1: { unit: '%', base: 52, amplitude: 14, noise: 2 },
  2: { unit: '°C', base: 21, amplitude: 6, noise: 0.8 },
  3: { unit: 'pH', base: 6.2, amplitude: 0.3, noise: 0.08 },
  4: { unit: '%', base: 66, amplitude: 9, noise: 2 },
  5: { unit: 'lx', base: 0, amplitude: 0, noise: 0 },
};

/** Deterministic pseudo-random number in [-1, 1) so the demo data is stable between renders. */
function jitter(seed: number): number {
  const x = Math.sin(seed * 12.9898) * 43758.5453;
  return (x - Math.floor(x)) * 2 - 1;
}

/** One reading per hour for the last 24 hours. Inactive sensors have none. */
export function buildReadings(idSensor: number): SensorReading[] {
  const sensor = sensors.find((candidate) => candidate.idSensor === idSensor);
  const profile = profiles[idSensor];
  if (!sensor || !sensor.active || !profile) return [];

  const readings: SensorReading[] = [];
  for (let hour = 23; hour >= 0; hour -= 1) {
    const wave = Math.sin(((23 - hour) / 24) * Math.PI * 2);
    const value = profile.base + wave * profile.amplitude + jitter(idSensor * 100 + hour) * profile.noise;
    readings.push({
      idReading: idSensor * 1000 + (23 - hour),
      idSensor,
      value: Math.round(value * 100) / 100,
      unit: profile.unit,
      recordedAt: hoursAgo(hour),
    });
  }
  return readings;
}

export const alerts: Alert[] = [
  {
    idAlert: 1,
    idCrop: 1,
    idSensor: 1,
    alertType: 'LOW_HUMIDITY',
    severity: 'HIGH',
    message: 'La humedad del suelo está por debajo del mínimo configurado.',
    detectedValue: 37.4,
    acknowledged: false,
    createdAt: hoursAgo(2),
  },
  {
    idAlert: 2,
    idCrop: 2,
    idSensor: 4,
    alertType: 'RECOMMENDED_WATERING',
    severity: 'MEDIUM',
    message: 'Se recomienda regar en las próximas horas.',
    detectedValue: 58.1,
    acknowledged: false,
    createdAt: hoursAgo(5),
  },
  {
    idAlert: 3,
    idCrop: 1,
    idSensor: 2,
    alertType: 'HIGH_TEMPERATURE',
    severity: 'LOW',
    message: 'La temperatura superó brevemente el máximo.',
    detectedValue: 35.6,
    acknowledged: true,
    createdAt: hoursAgo(27),
  },
  {
    idAlert: 4,
    idCrop: 3,
    idSensor: null,
    alertType: 'PEST_DETECTED',
    severity: 'VERY_HIGH',
    message: 'Posible presencia de plaga reportada en la parcela.',
    detectedValue: null,
    acknowledged: false,
    createdAt: hoursAgo(9),
  },
];

export const irrigations: Irrigation[] = [
  {
    idIrrigation: 1,
    idCrop: 1,
    startedAt: hoursAgo(20),
    endedAt: hoursAgo(19.5),
    durationMin: 30,
    waterLiters: 450,
    type: 'AUTOMATIC',
  },
  {
    idIrrigation: 2,
    idCrop: 2,
    startedAt: hoursAgo(44),
    endedAt: hoursAgo(43.25),
    durationMin: 45,
    waterLiters: 680,
    type: 'MANUAL',
  },
];
