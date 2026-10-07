import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { Plus, Pencil, Search } from 'lucide-react';
import { api, queryString } from '../../shared/api/client';
import type { Category, Page } from '../../shared/types';
import { useDebouncedValue } from '../../shared/hooks/useDebouncedValue';
import { useSession } from '../auth/session';
import { useToast } from '../../app/toast';
import { Button } from '../../shared/components/Button';
import { Field } from '../../shared/components/Field';
import { Dialog } from '../../shared/components/Dialog';
import { Pagination } from '../../shared/components/Pagination';
import { Loading, ErrorState, EmptyState } from '../../shared/components/Feedback';

const schema = z.object({ name: z.string().trim().min(1).max(100), active: z.boolean() });
function CategoryForm({
  category,
  onClose,
  onSavingChange,
}: {
  category?: Category;
  onClose: () => void;
  onSavingChange: (saving: boolean) => void;
}) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: category || { name: '', active: true },
  });
  const save = useMutation({
    onMutate: () => onSavingChange(true),
    mutationFn: (values: z.infer<typeof schema>) =>
      category ? api.put(`/categories/${category.id}`, values) : api.post('/categories', values),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['categories'] });
      toast(t('common.saved'));
      onClose();
    },
    onSettled: () => onSavingChange(false),
  });
  return (
    <form
      className="form-stack"
      onSubmit={handleSubmit((values) => save.mutate(values))}
      noValidate
    >
      <Field
        id="category-name"
        label={t('common.name')}
        error={errors.name && t('validation.required')}
      >
        <input id="category-name" autoFocus {...register('name')} disabled={save.isPending} />
      </Field>
      <label className="checkbox-label">
        <input type="checkbox" {...register('active')} disabled={save.isPending} />
        {t('common.active')}
      </label>
      {save.isError && <ErrorState error={save.error} />}
      <div className="dialog-actions">
        <Button variant="secondary" onClick={onClose} disabled={save.isPending}>
          {t('common.cancel')}
        </Button>
        <Button type="submit" busy={save.isPending}>
          {t('common.save')}
        </Button>
      </div>
    </form>
  );
}

export function CategoriesDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const session = useSession();
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Category | 'new' | null>(null);
  const [saving, setSaving] = useState(false);
  const debounced = useDebouncedValue(search);
  const query = useQuery({
    queryKey: ['categories', debounced, status, page],
    queryFn: () =>
      api.get<Page<Category>>(`/categories?${queryString({ search: debounced, status, page })}`),
  });
  const manager = session.data?.role === 'BUSINESS_USER';
  return (
    <Dialog
      title={t(
        editing ? (editing === 'new' ? 'categories.new' : 'categories.edit') : 'categories.title',
      )}
      onClose={() => {
        if (!saving) onClose();
      }}
      wide={!editing}
    >
      {editing ? (
        <CategoryForm
          category={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSavingChange={setSaving}
        />
      ) : (
        <>
          <div className="category-toolbar">
            <p className="muted small">{t('categories.description')}</p>
            {manager && (
              <Button onClick={() => setEditing('new')}>
                <Plus size={17} />
                {t('categories.new')}
              </Button>
            )}
          </div>
          <section className="panel">
            <div className="filters">
              <div className="search-input">
                <Search size={17} />
                <input
                  aria-label={t('common.search')}
                  placeholder={t('common.search')}
                  value={search}
                  onChange={(event) => {
                    setSearch(event.target.value);
                    setPage(0);
                  }}
                />
              </div>
              <select
                aria-label={t('common.status')}
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value);
                  setPage(0);
                }}
              >
                {['all', 'active', 'inactive'].map((value) => (
                  <option key={value} value={value}>
                    {t(`common.${value}`)}
                  </option>
                ))}
              </select>
            </div>
            {query.isPending ? (
              <Loading />
            ) : query.isError ? (
              <ErrorState error={query.error} />
            ) : (
              <>
                <div className="table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>{t('common.name')}</th>
                        <th>{t('common.status')}</th>
                        {manager && <th>{t('common.actions')}</th>}
                      </tr>
                    </thead>
                    <tbody>
                      {query.data.items.map((category) => (
                        <tr key={category.id}>
                          <td className="strong">{category.name}</td>
                          <td>
                            <span className="badge">
                              {t(category.active ? 'common.active' : 'common.inactive')}
                            </span>
                          </td>
                          {manager && (
                            <td>
                              <button
                                className="icon-button"
                                onClick={() => setEditing(category)}
                                aria-label={`${t('common.edit')} ${category.name}`}
                              >
                                <Pencil size={16} />
                              </button>
                            </td>
                          )}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {!query.data.items.length && <EmptyState title={t('categories.empty')} />}
                <Pagination page={page} total={query.data.total} onChange={setPage} />
              </>
            )}
          </section>
          <div className="dialog-actions">
            <Button variant="secondary" onClick={onClose}>
              {t('common.close')}
            </Button>
          </div>
        </>
      )}
    </Dialog>
  );
}
