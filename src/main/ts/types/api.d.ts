export interface ReadingPoint {
  recordedAt: string;
  value: number;
}

export interface ReadingSeries {
  sensorCode: string;
  unit: string | null;
  thresholdMin: number | null;
  thresholdMax: number | null;
  points: ReadingPoint[];
  windowHours: number;
  /** 'api': through the backend; 'database': read straight from the shared database (fallback). */
  source: 'api' | 'database';
  loadedAt: string;
}

export interface DailyWater {
  date: string;
  liters: number;
}

export interface EstateMarker {
  id: number;
  name: string;
  location: string | null;
  areaHa: number | null;
  latitude: number | null;
  longitude: number | null;
  crops: string[];
  sensorCount: number;
  openAlerts: number;
  sensors: SensorSpot[];
  boundary: Corner[] | null;
}

export type Corner = [number, number];

export interface SensorSpot {
  id: number;
  code: string;
  typeLabel: string;
  latitude: number;
  longitude: number;
  coverageM: number;
  active: boolean;
  lastValue: number | null;
  unit: string | null;
}

export interface PickerSite {
  name: string;
  latitude: number | null;
  longitude: number | null;
  areaHa: number | null;
  boundary: Corner[] | null;
}

export interface IrrigationRequest {
  cropId: number;
  durationMin: number;
  waterLiters: number;
}

export interface EmailAvailability {
  available: boolean;
}

export interface ApiMessage {
  message: string;
}
