// These shapes mirror the backend JPA entities, with relations flattened to ids.

export type CropStage =
  | 'GERMINATION'
  | 'GROWTH'
  | 'FLOWERING'
  | 'FRUITING'
  | 'RIPENING'
  | 'HARVEST';

export type SensorType =
  | 'SOIL_MOISTURE'
  | 'AIR_TEMPERATURE'
  | 'SOIL_TEMPERATURE'
  | 'PH'
  | 'CONDUCTIVITY'
  | 'LIGHT'
  | 'RAIN_GAUGE'
  | 'RELATIVE_HUMIDITY';

export type AlertType =
  | 'LOW_HUMIDITY'
  | 'HIGH_HUMIDITY'
  | 'HIGH_TEMPERATURE'
  | 'LOW_TEMPERATURE'
  | 'PH_OUT_OF_RANGE'
  | 'HIGH_CONDUCTIVITY'
  | 'PEST_DETECTED'
  | 'RECOMMENDED_WATERING'
  | 'WATER_STRESS';

export type Severity = 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';

export type IrrigationType = 'AUTOMATIC' | 'MANUAL';

export interface SessionUser {
  idUser: number;
  name: string;
  lastName: string;
  email: string;
  role: string;
}

export interface Estate {
  idEstate: number;
  name: string;
  location: string | null;
  areaHa: number | null;
}

export interface Crop {
  idCrop: number;
  idEstate: number;
  name: string;
  variety: string | null;
  sowingDate: string | null;
  stage: CropStage;
  humidityMin: number;
  humidityMax: number;
  tempMin: number;
  tempMax: number;
  phMin: number;
  phMax: number;
  active: boolean;
}

export interface Sensor {
  idSensor: number;
  idCrop: number;
  sensorCode: string;
  sensorType: SensorType;
  location: string | null;
  active: boolean;
  lastReadingAt: string | null;
}

export interface SensorReading {
  idReading: number;
  idSensor: number;
  value: number;
  unit: string;
  recordedAt: string;
}

export interface Alert {
  idAlert: number;
  idCrop: number;
  idSensor: number | null;
  alertType: AlertType;
  severity: Severity;
  message: string;
  detectedValue: number | null;
  acknowledged: boolean;
  createdAt: string;
}

export interface Irrigation {
  idIrrigation: number;
  idCrop: number;
  startedAt: string;
  endedAt: string | null;
  durationMin: number | null;
  waterLiters: number | null;
  type: IrrigationType;
}
