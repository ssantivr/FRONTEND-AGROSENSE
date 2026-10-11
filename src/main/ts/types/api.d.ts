// Shapes of the JSON the server sends, mirroring com.agrosense.frontend.dto.Views.
// BigDecimal arrives as a number; LocalDateTime and LocalDate arrive as ISO strings without a zone.

export interface ReadingPoint {
  recordedAt: string;
  value: number;
}

/** GET /api/ui/sensors/{id}/readings */
export interface ReadingSeries {
  sensorCode: string;
  unit: string | null;
  thresholdMin: number | null;
  thresholdMax: number | null;
  points: ReadingPoint[];
}

/** One item of GET /api/ui/water-usage */
export interface DailyWater {
  date: string;
  liters: number;
}

/** One item of GET /api/ui/estates/markers */
export interface EstateMarker {
  id: number;
  name: string;
  location: string | null;
  areaHa: number | null;
  latitude: number | null;
  longitude: number | null;
  /** Names of the active crops. */
  crops: string[];
  sensorCount: number;
  openAlerts: number;
  /** The sensors that have been placed on the map. */
  sensors: SensorSpot[];
  boundary: Corner[] | null;
}

/** One corner of an estate's outline: latitude, then longitude. */
export type Corner = [number, number];

/** A sensor with a position and the radius, in metres, of the ground its readings stand for. */
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

/** An estate as the sensor position picker needs it. */
export interface PickerSite {
  name: string;
  latitude: number | null;
  longitude: number | null;
  areaHa: number | null;
  boundary: Corner[] | null;
}

/** Body of POST /api/ui/irrigations */
export interface IrrigationRequest {
  cropId: number;
  durationMin: number;
  waterLiters: number;
}

/** GET /registro/disponibilidad */
export interface EmailAvailability {
  available: boolean;
}

/** Body of every error answer of the JSON endpoints. */
export interface ApiMessage {
  message: string;
}
