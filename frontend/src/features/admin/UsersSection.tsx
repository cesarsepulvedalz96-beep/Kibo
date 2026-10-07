import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslation } from 'react-i18next';
import { KeyRound, Pencil, Plus, UserCheck, UserX } from 'lucide-react';
import type { AdminUser } from '../../shared/types';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/ConfirmDialog';
import { Dialog } from '../../shared/components/Dialog';
import { EmptyState, ErrorState, Loading } from '../../shared/components/Feedback';
import { Field } from '../../shared/components/Field';
import { Pagination } from '../../shared/components/Pagination';
import { dateTime } from '../../shared/format';
import { useToast } from '../../app/toast';
import { adminApi, adminKeys } from './api';

const userSchema = z
  .object({
    businessId: z.string().min(1),
    email: z.email().max(254),
    displayName: z.string().trim().min(1).max(120),
    temporaryPassword: z.string().max(64).optional(),
  })
  .refine(
    (v) =>
      !v.temporaryPassword ||
      (v.temporaryPassword.length >= 12 &&
        new TextEncoder().encode(v.temporaryPassword).length <= 72),
    { path: ['temporaryPassword'] },
  );
type UserValues = z.infer<typeof userSchema>;

function UserForm({
  user,
  businesses,
  onClose,
}: {
  user?: AdminUser;
  businesses: { id: string; name: string }[];
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const schema = user
    ? userSchema
    : userSchema.refine((v) => Boolean(v.temporaryPassword), { path: ['temporaryPassword'] });
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<UserValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      businessId: user?.businessId || '',
      email: user?.email || '',
      displayName: user?.displayName || '',
      temporaryPassword: '',
    },
  });
  const save = useMutation({
    mutationFn: (v: UserValues) =>
      user
        ? adminApi.updateUser(user.id, { email: v.email, displayName: v.displayName })
        : adminApi.createUser({
            businessId: v.businessId,
            email: v.email,
            displayName: v.displayName,
            temporaryPassword: v.temporaryPassword!,
          }),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.users });
      toast(t('common.saved'));
      onClose();
    },
  });
  return (
    <Dialog title={t(user ? 'admin.editUser' : 'admin.newUser')} onClose={onClose} wide>
      <form className="form-stack" onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <div className="form-grid">
          <Field
            id="display-name"
            label={t('common.name')}
            error={errors.displayName && t('validation.required')}
          >
            <input id="display-name" autoFocus {...register('displayName')} />
          </Field>
          <Field
            id="user-email"
            label={t('login.email')}
            error={errors.email && t('validation.email')}
          >
            <input id="user-email" type="email" {...register('email')} />
          </Field>
          <Field
            id="user-business"
            label={t('admin.business')}
            hint={user ? t('admin.businessImmutable') : undefined}
            error={errors.businessId && t('validation.required')}
          >
            <select id="user-business" disabled={Boolean(user)} {...register('businessId')}>
              <option value="">{t('admin.selectBusiness')}</option>
              {businesses.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.name}
                </option>
              ))}
            </select>
          </Field>
          {!user && (
            <Field
              id="temporary-password"
              label={t('admin.temporaryPassword')}
              hint={t('admin.temporaryPasswordHint')}
              error={errors.temporaryPassword && t('validation.password')}
            >
              <input
                id="temporary-password"
                type="password"
                autoComplete="new-password"
                {...register('temporaryPassword')}
              />
            </Field>
          )}
        </div>
        {save.isError && <ErrorState error={save.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" busy={save.isPending}>
            {t('common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  );
}

function ResetPasswordDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const { t } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [password, setPassword] = useState('');
  const reset = useMutation({
    mutationFn: () => adminApi.resetPassword(user.id, password),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.users });
      toast(t('admin.passwordReset'));
      onClose();
    },
  });
  const valid = password.length >= 12 && new TextEncoder().encode(password).length <= 72;
  return (
    <Dialog title={t('admin.resetPassword')} onClose={onClose}>
      <div className="form-stack">
        <p>{t('admin.resetPasswordDescription', { name: user.displayName })}</p>
        <Field
          id="reset-password"
          label={t('admin.temporaryPassword')}
          hint={t('validation.password')}
        >
          <input
            id="reset-password"
            type="password"
            autoFocus
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </Field>
        {reset.isError && <ErrorState error={reset.error} />}
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button disabled={!valid} busy={reset.isPending} onClick={() => reset.mutate()}>
            {t('admin.resetPassword')}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

export function UsersSection() {
  const { t, i18n } = useTranslation();
  const client = useQueryClient();
  const toast = useToast();
  const [page, setPage] = useState(0);
  const [businessId, setBusinessId] = useState('');
  const [active, setActive] = useState('');
  const [editing, setEditing] = useState<AdminUser | 'new' | null>(null);
  const [resetting, setResetting] = useState<AdminUser | null>(null);
  const [changing, setChanging] = useState<AdminUser | null>(null);
  const businesses = useQuery({
    queryKey: [...adminKeys.businesses, 'lookup'],
    queryFn: adminApi.businessLookup,
  });
  const users = useQuery({
    queryKey: [...adminKeys.users, page, businessId, active],
    queryFn: () =>
      adminApi.users({ page, businessId: businessId || undefined, active: active || undefined }),
  });
  const change = useMutation({
    mutationFn: (u: AdminUser) => adminApi.userAction(u.id, u.active ? 'suspend' : 'reactivate'),
    onSuccess: async () => {
      await client.invalidateQueries({ queryKey: adminKeys.users });
      toast(t('common.saved'));
      setChanging(null);
    },
  });
  return (
    <section>
      <div className="section-toolbar">
        <div>
          <h2>{t('admin.users')}</h2>
          <p>{t('admin.usersDescription')}</p>
        </div>
        <Button onClick={() => setEditing('new')}>
          <Plus size={17} />
          {t('admin.newUser')}
        </Button>
      </div>
      <div className="filters admin-filters">
        <Field id="users-business" label={t('admin.business')}>
          <select
            id="users-business"
            value={businessId}
            onChange={(e) => {
              setBusinessId(e.target.value);
              setPage(0);
            }}
          >
            <option value="">{t('common.all')}</option>
            {businesses.data?.map((b) => (
              <option key={b.id} value={b.id}>
                {b.name}
              </option>
            ))}
          </select>
        </Field>
        <Field id="users-status" label={t('common.status')}>
          <select
            id="users-status"
            value={active}
            onChange={(e) => {
              setActive(e.target.value);
              setPage(0);
            }}
          >
            <option value="">{t('common.all')}</option>
            <option value="true">{t('common.active')}</option>
            <option value="false">{t('common.inactive')}</option>
          </select>
        </Field>
      </div>
      <div className="panel">
        {users.isPending ? (
          <Loading />
        ) : users.isError ? (
          <ErrorState error={users.error} />
        ) : (
          <>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr>
                    <th>{t('common.name')}</th>
                    <th>{t('login.email')}</th>
                    <th>{t('admin.business')}</th>
                    <th>{t('common.status')}</th>
                    <th>{t('admin.lastAccess')}</th>
                    <th className="numeric">{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {users.data.items.map((u) => (
                    <tr key={u.id}>
                      <td className="strong">
                        {u.displayName}
                        {u.mustChangePassword && (
                          <span className="badge badge--spaced">{t('admin.passwordPending')}</span>
                        )}
                      </td>
                      <td>{u.email}</td>
                      <td>{u.businessName || '—'}</td>
                      <td>
                        <span className="badge">
                          {t(u.active ? 'common.active' : 'common.inactive')}
                        </span>
                      </td>
                      <td>
                        {u.lastLoginAt ? dateTime(u.lastLoginAt, i18n.language) : t('admin.never')}
                      </td>
                      <td>
                        <div className="row-actions">
                          <button
                            className="icon-button"
                            aria-label={`${t('common.edit')} ${u.displayName}`}
                            onClick={() => setEditing(u)}
                          >
                            <Pencil size={16} />
                          </button>
                          <button
                            className="icon-button"
                            aria-label={`${t('admin.resetPassword')} ${u.displayName}`}
                            onClick={() => setResetting(u)}
                          >
                            <KeyRound size={16} />
                          </button>
                          <button
                            className="icon-button"
                            aria-label={`${t(u.active ? 'admin.suspend' : 'admin.reactivate')} ${u.displayName}`}
                            onClick={() => setChanging(u)}
                          >
                            {u.active ? <UserX size={16} /> : <UserCheck size={16} />}
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!users.data.items.length && <EmptyState title={t('common.noData')} />}
            <Pagination page={page} total={users.data.total} onChange={setPage} />
          </>
        )}
      </div>
      {editing && (
        <UserForm
          user={editing === 'new' ? undefined : editing}
          businesses={businesses.data || []}
          onClose={() => setEditing(null)}
        />
      )}
      {resetting && <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)} />}
      {changing && (
        <ConfirmDialog
          title={t(changing.active ? 'admin.suspend' : 'admin.reactivate')}
          description={t(
            changing.active ? 'admin.suspendDescription' : 'admin.reactivateDescription',
            { name: changing.displayName },
          )}
          confirmLabel={t(changing.active ? 'admin.suspend' : 'admin.reactivate')}
          danger={changing.active}
          busy={change.isPending}
          onClose={() => setChanging(null)}
          onConfirm={() => change.mutate(changing)}
        />
      )}
    </section>
  );
}
