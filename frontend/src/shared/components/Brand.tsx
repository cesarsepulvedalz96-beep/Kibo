export function Brand({ complete = false }: { complete?: boolean }) {
  const suffix = complete ? '-completo' : '';
  return (
    <span className={`brand ${complete ? 'brand--complete' : 'brand--compact'}`}>
      <img
        className="brand-logo brand-logo--light-theme"
        src={`/logos/logo${suffix}-oscuro.webp`}
        alt="Kivo"
        decoding="async"
      />
      <img
        className="brand-logo brand-logo--dark-theme"
        src={`/logos/logo${suffix}-claro.webp`}
        alt="Kivo"
        decoding="async"
      />
    </span>
  );
}
