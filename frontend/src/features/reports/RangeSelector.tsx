import { useTranslation } from 'react-i18next';

export type ReportRange = 'TODAY' | 'WEEK' | 'MONTH' | 'YEAR';
export function RangeSelector({
  value,
  onChange,
}: {
  value: ReportRange;
  onChange: (range: ReportRange) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="segmented-control" role="group" aria-label={t('reports.period')}>
      {(['TODAY', 'WEEK', 'MONTH', 'YEAR'] as const).map((range) => (
        <button key={range} aria-pressed={value === range} onClick={() => onChange(range)}>
          {t(`range.${range}`)}
        </button>
      ))}
    </div>
  );
}
