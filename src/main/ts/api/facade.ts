import type { DailyWater, EmailAvailability, EstateMarker, IrrigationRequest, ReadingSeries } from '../types/api.js';
import { CachingApiProxy } from './cache-proxy.js';
import { checkEmail, fetchEstateMarkers, fetchReadings, fetchWaterUsage, startIrrigation } from './ui.js';

// What a page may ask of the server. Pages depend on this, not on routes, guards or how a request is retried.
export interface ApiPort {
  readings(sensorId: string, hours: number, signal?: AbortSignal): Promise<ReadingSeries>;
  waterUsage(): Promise<DailyWater[]>;
  estateMarkers(): Promise<EstateMarker[]>;
  startIrrigation(irrigation: IrrigationRequest): Promise<Record<string, unknown>>;
  checkEmail(url: string, email: string, signal: AbortSignal): Promise<EmailAvailability>;
}

export class ApiFacade implements ApiPort {
  readings(sensorId: string, hours: number, signal?: AbortSignal): Promise<ReadingSeries> {
    return fetchReadings(sensorId, hours, signal);
  }

  waterUsage(): Promise<DailyWater[]> {
    return fetchWaterUsage();
  }

  estateMarkers(): Promise<EstateMarker[]> {
    return fetchEstateMarkers();
  }

  startIrrigation(irrigation: IrrigationRequest): Promise<Record<string, unknown>> {
    return startIrrigation(irrigation);
  }

  checkEmail(url: string, email: string, signal: AbortSignal): Promise<EmailAvailability> {
    return checkEmail(url, email, signal);
  }
}

export const api: ApiPort = new CachingApiProxy(new ApiFacade());
