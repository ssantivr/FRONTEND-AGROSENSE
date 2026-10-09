import type { Alert, Crop, Estate, Irrigation, Sensor, SensorReading, SessionUser } from '../types';
import * as mock from './mockData';

export interface DataSource {
  login(email: string, password: string): Promise<SessionUser>;
  logout(): void;
  getEstates(): Promise<Estate[]>;
  getCrops(): Promise<Crop[]>;
  getSensors(): Promise<Sensor[]>;
  getReadings(idSensor: number): Promise<SensorReading[]>;
  getAlerts(): Promise<Alert[]>;
  acknowledgeAlert(idAlert: number): Promise<Alert>;
  getIrrigations(): Promise<Irrigation[]>;
  startIrrigation(idCrop: number, durationMin: number): Promise<Irrigation>;
}

/** Error whose message is safe to show to the user (written in Spanish). */
export class UserFacingError extends Error {}

/** In-memory data source used when no backend URL is configured. */
export function createMockDataSource(): DataSource {
  const alerts = mock.alerts.map((alert) => ({ ...alert }));
  const irrigations = mock.irrigations.map((irrigation) => ({ ...irrigation }));

  return {
    async login(email, password) {
      if (email.trim().toLowerCase() !== mock.DEMO_EMAIL || password !== mock.DEMO_PASSWORD) {
        throw new UserFacingError('Correo o contraseña incorrectos.');
      }
      return mock.demoUser;
    },
    logout() {},
    async getEstates() {
      return mock.estates;
    },
    async getCrops() {
      return mock.crops;
    },
    async getSensors() {
      return mock.sensors;
    },
    async getReadings(idSensor) {
      return mock.buildReadings(idSensor);
    },
    async getAlerts() {
      return alerts.map((alert) => ({ ...alert }));
    },
    async acknowledgeAlert(idAlert) {
      const alert = alerts.find((candidate) => candidate.idAlert === idAlert);
      if (!alert) throw new UserFacingError('La alerta ya no existe.');
      alert.acknowledged = true;
      return { ...alert };
    },
    async getIrrigations() {
      return irrigations.map((irrigation) => ({ ...irrigation }));
    },
    async startIrrigation(idCrop, durationMin) {
      const irrigation: Irrigation = {
        idIrrigation: Math.max(0, ...irrigations.map((item) => item.idIrrigation)) + 1,
        idCrop,
        startedAt: new Date().toISOString(),
        endedAt: null,
        durationMin,
        waterLiters: null,
        type: 'MANUAL',
      };
      irrigations.unshift(irrigation);
      return { ...irrigation };
    },
  };
}

/**
 * REST data source for the Spring backend, which authenticates with HTTP Basic.
 * Credentials are kept in memory only, so reloading the page signs the user out.
 */
export function createHttpDataSource(baseUrl: string): DataSource {
  let authorization: string | null = null;

  async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    let response: Response;
    try {
      response = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, {
        ...init,
        headers: {
          Accept: 'application/json',
          ...(init.body ? { 'Content-Type': 'application/json' } : {}),
          ...(authorization ? { Authorization: authorization } : {}),
        },
      });
    } catch {
      throw new UserFacingError('No se pudo conectar con el servidor.');
    }
    if (response.status === 401) {
      throw new UserFacingError('Correo o contraseña incorrectos.');
    }
    if (!response.ok) {
      throw new UserFacingError(`El servidor respondió con un error (${response.status}).`);
    }
    return (await response.json()) as T;
  }

  return {
    async login(email, password) {
      const bytes = new TextEncoder().encode(`${email}:${password}`);
      authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`;
      try {
        return await request<SessionUser>('/api/users/me');
      } catch (error) {
        authorization = null;
        throw error;
      }
    },
    logout() {
      authorization = null;
    },
    getEstates: () => request('/api/estates'),
    getCrops: () => request('/api/crops'),
    getSensors: () => request('/api/sensors'),
    getReadings: (idSensor) => request(`/api/sensors/${idSensor}/readings`),
    getAlerts: () => request('/api/alerts'),
    acknowledgeAlert: (idAlert) =>
      request(`/api/alerts/${idAlert}/acknowledge`, { method: 'PATCH' }),
    getIrrigations: () => request('/api/irrigations'),
    startIrrigation: (idCrop, durationMin) =>
      request('/api/irrigations', {
        method: 'POST',
        body: JSON.stringify({ idCrop, durationMin }),
      }),
  };
}

const apiUrl = (import.meta.env.VITE_API_URL as string | undefined)?.trim();

export const isDemoMode = !apiUrl;

export const dataSource: DataSource = apiUrl
  ? createHttpDataSource(apiUrl)
  : createMockDataSource();
