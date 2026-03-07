import { useState } from 'react';
import { DateRange } from '../api/client';
import './DateRangeFilter.css';

export type PresetKey = 'all' | '15m' | '1h' | '6h' | '24h' | '7d' | '30d' | '90d' | 'custom';

export interface Preset {
  key: PresetKey;
  label: string;
  minutes?: number;
}

const DEFAULT_PRESETS: Preset[] = [
  { key: 'all', label: 'All time' },
  { key: '15m', label: '15 min', minutes: 15 },
  { key: '1h', label: '1 hour', minutes: 60 },
  { key: '6h', label: '6 hours', minutes: 360 },
  { key: '24h', label: '24 hours', minutes: 1440 },
  { key: '7d', label: '7 days', minutes: 10080 },
  { key: 'custom', label: 'Custom' },
];

interface DateRangeFilterProps {
  onChange: (range: DateRange) => void;
  presets?: Preset[];
}

function toLocalDatetime(date: Date): string {
  const offset = date.getTimezoneOffset();
  const local = new Date(date.getTime() - offset * 60000);
  return local.toISOString().slice(0, 16);
}

export default function DateRangeFilter({ onChange, presets }: DateRangeFilterProps) {
  const [active, setActive] = useState<PresetKey>('all');
  const [customFrom, setCustomFrom] = useState('');
  const [customTo, setCustomTo] = useState('');

  const activePresets = presets ?? DEFAULT_PRESETS;

  const handlePreset = (preset: Preset) => {
    setActive(preset.key);

    if (preset.key === 'all') {
      onChange({});
    } else if (preset.key === 'custom') {
      // initialize custom inputs to sensible defaults
      const now = new Date();
      const oneHourAgo = new Date(now.getTime() - 3600000);
      setCustomFrom(toLocalDatetime(oneHourAgo));
      setCustomTo(toLocalDatetime(now));
      onChange({
        from: oneHourAgo.toISOString(),
        to: now.toISOString(),
      });
    } else if (preset.minutes) {
      const now = new Date();
      const from = new Date(now.getTime() - preset.minutes * 60000);
      onChange({ from: from.toISOString(), to: now.toISOString() });
    }
  };

  const handleCustomChange = (field: 'from' | 'to', value: string) => {
    const nextFrom = field === 'from' ? value : customFrom;
    const nextTo = field === 'to' ? value : customTo;

    if (field === 'from') setCustomFrom(value);
    if (field === 'to') setCustomTo(value);

    if (nextFrom && nextTo) {
      onChange({
        from: new Date(nextFrom).toISOString(),
        to: new Date(nextTo).toISOString(),
      });
    }
  };

  return (
    <div className="date-range-filter">
      <div className="preset-buttons">
        {activePresets.map((preset) => (
          <button
            key={preset.key}
            type="button"
            className={`preset-btn${active === preset.key ? ' active' : ''}`}
            onClick={() => handlePreset(preset)}
          >
            {preset.label}
          </button>
        ))}
      </div>

      {active === 'custom' && (
        <div className="custom-range">
          <label className="custom-label">
            From
            <input
              type="datetime-local"
              className="custom-input"
              value={customFrom}
              onChange={(e) => handleCustomChange('from', e.target.value)}
            />
          </label>
          <label className="custom-label">
            To
            <input
              type="datetime-local"
              className="custom-input"
              value={customTo}
              onChange={(e) => handleCustomChange('to', e.target.value)}
            />
          </label>
        </div>
      )}
    </div>
  );
}
