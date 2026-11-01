export const RANGE_FIELDS = ['humidityMin', 'humidityMax', 'tempMin', 'tempMax', 'phMin', 'phMax'];
// Copies the template's ranges into the fields, which stay editable. Choosing "no template" leaves them as they are.
export function fillRanges(ranges, fieldOf) {
    for (const name of RANGE_FIELDS) {
        const value = ranges[name];
        const field = fieldOf(name);
        if (value !== undefined && field)
            field.value = value;
    }
}
