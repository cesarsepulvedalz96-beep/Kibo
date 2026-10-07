INSERT INTO notifications(id, business_id, product_id, product_name, stock, read, created_at)
SELECT gen_random_uuid(), p.business_id, p.id, p.name, p.stock, FALSE, now()
FROM products p
JOIN business_settings s ON s.business_id = p.business_id
WHERE p.active
  AND p.stock <= p.minimum_stock
  AND s.stock_alerts
  AND NOT EXISTS (
      SELECT 1
      FROM notifications n
      WHERE n.business_id = p.business_id
        AND n.product_id = p.id
        AND NOT n.read
  );
