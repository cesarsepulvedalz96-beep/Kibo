import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { Product } from '../../shared/types';
import { operationKey, usePendingOperation } from '../../shared/hooks/usePendingOperation';
import { useSession } from '../auth/session';
import { useToast } from '../../app/toast';
import { MovementProductSelector } from './MovementProductSelector';
import { Dialog } from '../../shared/components/Dialog';
import { Field } from '../../shared/components/Field';
import { Button } from '../../shared/components/Button';
import { ErrorState } from '../../shared/components/Feedback';

const schema = z.object({
  type: z.enum(['PURCHASE_ENTRY', 'MANUAL_ENTRY', 'MANUAL_EXIT', 'ADJUSTMENT']),
  quantity: z.number().int().min(0).max(1_000_000),
});

interface MovementContext {
  product: Product;
  values: z.infer<typeof schema>;
}

export function MovementForm({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const session = useSession();
  const operation = usePendingOperation<MovementContext>(operationKey(session.data!, 'movement'));
  const [product, setProduct] = useState<Product | undefined>(operation.pending?.context.product);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: operation.pending?.context.values || { type: 'PURCHASE_ENTRY', quantity: 1 },
  });
  const save = useMutation({
    mutationFn: (values: z.infer<typeof schema>) =>
      operation.execute(
        '/inventory',
        { ...values, productId: product!.id },
        { product: product!, values },
      ),
    onSuccess: async () => {
      await Promise.all(
        ['products', 'inventory', 'reports', 'notifications'].map((key) =>
          client.invalidateQueries({ queryKey: [key] }),
        ),
      );
      toast(t('common.saved'));
      onClose();
    },
  });
  const locked = save.isPending || Boolean(operation.pending);
  const close = () => {
    if (!locked) onClose();
  };
  if (operation.recoveryError)
    return (
      <Dialog title={t('inventory.new')} onClose={onClose}>
        <ErrorState error={operation.recoveryError} retry={() => window.location.reload()} />
      </Dialog>
    );
  return (
    <Dialog title={t('inventory.new')} onClose={close} wide>
      <form
        className="form-stack"
        onSubmit={(event) => {
          if (operation.pending) {
            event.preventDefault();
            save.mutate(operation.pending.context.values);
          } else void handleSubmit((values) => save.mutate(values))(event);
        }}
        noValidate
      >
        <MovementProductSelector product={product} onChange={setProduct} disabled={locked} />
        {product && (
          <div className="selected-product">
            <strong>{product.name}</strong>
            <span>
              {t('products.stock')}: {product.stock}
            </span>
          </div>
        )}
        <Field id="movement-type" label={t('inventory.type')}>
          <select id="movement-type" {...register('type')} disabled={locked}>
            {['PURCHASE_ENTRY', 'MANUAL_ENTRY', 'MANUAL_EXIT', 'ADJUSTMENT'].map((type) => (
              <option key={type} value={type}>
                {t(`movement.${type}`)}
              </option>
            ))}
          </select>
        </Field>
        <Field
          id="quantity"
          label={t('inventory.quantity')}
          hint={t('inventory.adjustHint')}
          error={errors.quantity && t('validation.required')}
        >
          <input
            id="quantity"
            type="number"
            min="0"
            step="1"
            disabled={locked}
            {...register('quantity', { valueAsNumber: true })}
          />
        </Field>
        {save.isError && <ErrorState error={save.error} />}
        {operation.pending && !save.isPending && <p role="status">{t('operation.uncertain')}</p>}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={close} disabled={locked}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" busy={save.isPending} disabled={!product}>
            {t(operation.pending ? 'operation.retry' : 'inventory.confirm')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}
