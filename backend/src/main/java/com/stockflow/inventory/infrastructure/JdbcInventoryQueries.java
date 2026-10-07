package com.stockflow.inventory.infrastructure;

import com.stockflow.inventory.application.InventoryQueries;
import com.stockflow.inventory.domain.MovementType;
import com.stockflow.shared.domain.Page;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcInventoryQueries implements InventoryQueries {
    private final JdbcTemplate jdbc;

    public JdbcInventoryQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Page<MovementView> list(
            UUID businessId, UUID productId, MovementType type, int page, int size) {
        String predicate =
                " where m.business_id = ?"
                        + (productId == null ? "" : " and m.product_id = ?")
                        + (type == null ? "" : " and m.type = ?");
        var params = new java.util.ArrayList<Object>();
        params.add(businessId);
        if (productId != null) params.add(productId);
        if (type != null) params.add(type.name());
        long count =
                jdbc.queryForObject(
                        "select count(*) from inventory_movements m" + predicate,
                        Long.class,
                        params.toArray());
        params.add(size);
        params.add(page * size);
        var items =
                jdbc.query(
                        """
                select m.*, p.name as product_name, u.display_name as user_name from inventory_movements m
                join products p on p.business_id = m.business_id and p.id = m.product_id
                join users u on u.business_id = m.business_id and u.id = m.performed_by
                """
                                + predicate
                                + " order by m.created_at desc, m.id limit ? offset ?",
                        (rs, row) ->
                                new MovementView(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("product_id", UUID.class),
                                        rs.getString("product_name"),
                                        rs.getString("type"),
                                        rs.getInt("quantity"),
                                        rs.getInt("previous_stock"),
                                        rs.getInt("new_stock"),
                                        rs.getObject("reference_id", UUID.class),
                                        rs.getString("user_name"),
                                        rs.getTimestamp("created_at").toInstant()),
                        params.toArray());
        return new Page<>(items, count, page, size);
    }
}
