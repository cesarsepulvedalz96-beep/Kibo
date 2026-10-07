import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { api, request } from '../../shared/api/client';
import type { Product, ProductInput } from '../../shared/types';
import { useCategories } from '../categories/queries';
import { Dialog } from '../../shared/components/Dialog';
import { Field } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { ErrorState, Loading } from '../../shared/components/Feedback';
import { useToast } from '../../app/toast';

const schema = z.object({
  name: z.string().trim().min(1).max(160),
  categoryId: z.uuid(),
  description: z.string().max(2000).nullable(),
  barcode: z.string().max(80).nullable(),
  salePrice: z.number().min(0).max(999999999999),
  costPrice: z.number().min(0).nullable(),
  minimumStock: z.number().int().min(0).max(1_000_000),
  active: z.boolean(),
});

export function ProductForm({ product, onClose }: { product?: Product; onClose: () => void }) {
  const { t } = useTranslation();
  const categories = useCategories();
  const client = useQueryClient();
  const toast = useToast();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ProductInput>({
    resolver: zodResolver(schema),
    defaultValues: product
      ? {
          categoryId: product.categoryId,
          name: product.name,
          description: product.description || '',
          barcode: product.barcode || '',
          salePrice: product.salePrice,
          costPrice: product.costPrice,
          minimumStock: product.minimumStock,
          active: product.active,
        }
      : {
          name: '',
          categoryId: '',
          description: '',
          barcode: '',
          salePrice: 0,
          costPrice: null,
          minimumStock: 5,
          active: true,
        },
  });
  const save = useMutation({
    mutationFn: (values: ProductInput) =>
      product
        ? api.put<Product>(`/products/${product.id}`, values)
        : api.post<Product>('/products', values),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: ['products'] });
      toast(t('common.saved'));
      onClose();
    },
  });
  const upload = useMutation({
    mutationFn: (file: File) => {
      const body = new FormData();
      body.append('file', file);
      return request<Product>(`/storage/products/${product!.id}`, { method: 'POST', body });
    },
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ['products'] });
      toast(t('common.saved'));
    },
  });
  const invalid = (key: keyof ProductInput) => (errors[key] ? t('validation.required') : undefined);

  return (
    <Dialog title={t(product ? 'products.edit' : 'products.new')} onClose={onClose} wide>
      <form
        onSubmit={handleSubmit((values) => save.mutate(values))}
        className="form-stack"
        noValidate
      >
        <div className="form-grid">
          <Field id="product-name" label={t('products.product')} error={invalid('name')}>
            <input id="product-name" autoFocus {...register('name')} aria-invalid={!!errors.name} />
          </Field>
          <Field id="category" label={t('products.category')} error={invalid('categoryId')}>
            <select id="category" {...register('categoryId')}>
              <option value="">{t('products.selectCategory')}</option>
              {categories.data
                ?.filter((category) => category.active || category.id === product?.categoryId)
                .map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
            </select>
          </Field>
          <Field id="barcode" label={t('products.barcode')} error={invalid('barcode')}>
            <input id="barcode" {...register('barcode')} />
          </Field>
          <Field id="minimum" label={t('products.minimum')} error={invalid('minimumStock')}>
            <input
              id="minimum"
              type="number"
              min="0"
              step="1"
              {...register('minimumStock', { valueAsNumber: true })}
            />
          </Field>
          <Field id="price" label={`${t('products.price')} · CLP`} error={invalid('salePrice')}>
            <input
              id="price"
              type="number"
              min="0"
              step="0.01"
              {...register('salePrice', { valueAsNumber: true })}
            />
          </Field>
          <Field id="cost" label={`${t('products.cost')} · CLP`} error={invalid('costPrice')}>
            <input
              id="cost"
              type="number"
              min="0"
              step="0.01"
              {...register('costPrice', {
                setValueAs: (value: string) => (value === '' ? null : Number(value)),
              })}
            />
          </Field>
        </div>
        <p className="field-hint">{t('products.priceHint')}</p>
        <Field
          id="description"
          label={t('products.descriptionField')}
          error={invalid('description')}
        >
          <textarea id="description" rows={3} {...register('description')} />
        </Field>
        <label className="checkbox-label">
          <input type="checkbox" {...register('active')} />
          {t('common.active')}
        </label>
        {!product && <div className="inline-note">{t('products.stockHint')}</div>}
        {product && (
          <Field id="image" label={t('products.image')} hint={t('products.imageHint')}>
            <input
              id="image"
              type="file"
              accept="image/png,image/jpeg"
              disabled={upload.isPending}
              onChange={(event) => {
                const file = event.target.files?.[0];
                if (file) upload.mutate(file);
              }}
            />
          </Field>
        )}
        {upload.isPending && <Loading />}
        {upload.isError && <ErrorState error={upload.error} />}
        {categories.isError && <ErrorState error={categories.error} />}
        {save.isError && <ErrorState error={save.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            type="submit"
            busy={save.isPending}
            disabled={upload.isPending || !categories.data}
          >
            {t(product ? 'common.save' : 'products.new')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
