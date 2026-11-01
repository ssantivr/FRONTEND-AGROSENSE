export interface TemplateRanges {
  humidityMin?: string | undefined;
  humidityMax?: string | undefined;
  tempMin?: string | undefined;
  tempMax?: string | undefined;
  phMin?: string | undefined;
  phMax?: string | undefined;
}

export const RANGE_FIELDS = ['humidityMin', 'humidityMax', 'tempMin', 'tempMax', 'phMin', 'phMax'] as const;

// Copies the template's ranges into the fields, which stay editable. Choosing "no template" leaves them as they are.
export function fillRanges(ranges: TemplateRanges, fieldOf: (name: string) => { value: string } | null): void {
  for (const name of RANGE_FIELDS) {
    const value = ranges[name];
    const field = fieldOf(name);
    if (value !== undefined && field) field.value = value;
  }
}
