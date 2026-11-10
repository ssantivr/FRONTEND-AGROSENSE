// What the crop page says about its field device, from the newest stored reading. No DOM here, so it is tested on its
// own (src/test/ts).
const time = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit', hour12: false });
const dayAndTime = new Intl.DateTimeFormat('es-CO', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
});
export function deviceStatus(lastReading, now, onlineWithinMs) {
    if (!lastReading || Number.isNaN(lastReading.getTime())) {
        return { state: 'none', label: 'ESP32 sin datos todavía' };
    }
    if (now.getTime() - lastReading.getTime() <= onlineWithinMs) {
        return { state: 'online', label: 'ESP32 conectado' };
    }
    const sameDay = lastReading.toDateString() === now.toDateString();
    return { state: 'stale', label: `ESP32 sin datos desde ${(sameDay ? time : dayAndTime).format(lastReading)}` };
}
/** The newest of the readings' times, ignoring those without one. */
export function newest(times) {
    let latest = null;
    for (const text of times) {
        if (!text)
            continue;
        const when = new Date(text);
        if (!Number.isNaN(when.getTime()) && (!latest || when > latest))
            latest = when;
    }
    return latest;
}
