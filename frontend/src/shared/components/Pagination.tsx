import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';

export function Pagination({
  page,
  size = 20,
  total,
  onChange,
}: {
  page: number;
  size?: number;
  total: number;
  onChange: (page: number) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="pagination">
      <span>
        {t('common.results', {
          from: total ? page * size + 1 : 0,
          to: Math.min((page + 1) * size, total),
          total,
        })}
      </span>
      <div>
        <button
          className="icon-button"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
          aria-label={t('common.previous')}
        >
          <ChevronLeft size={18} />
        </button>
        <span>{page + 1}</span>
        <button
          className="icon-button"
          disabled={(page + 1) * size >= total}
          onClick={() => onChange(page + 1)}
          aria-label={t('common.next')}
        >
          <ChevronRight size={18} />
        </button>
      </div>
    </div>
  );
}
