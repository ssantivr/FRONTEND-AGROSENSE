import { CachingApiProxy } from './cache-proxy.js';
import { checkEmail, fetchEstateMarkers, fetchReadings, fetchWaterUsage, startIrrigation } from './ui.js';
export class ApiFacade {
    readings(sensorId, hours, signal) {
        return fetchReadings(sensorId, hours, signal);
    }
    waterUsage() {
        return fetchWaterUsage();
    }
    estateMarkers() {
        return fetchEstateMarkers();
    }
    startIrrigation(irrigation) {
        return startIrrigation(irrigation);
    }
    checkEmail(url, email, signal) {
        return checkEmail(url, email, signal);
    }
}
export const api = new CachingApiProxy(new ApiFacade());
