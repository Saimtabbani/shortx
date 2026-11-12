package com.shortx.repository.projection;

/**
 * Interface projection for a "GROUP BY dimension, COUNT(*)" query result.
 * Spring Data proxies this at runtime — no implementation class needed —
 * which keeps the repository from having to hand back raw {@code Object[]}
 * rows or a full entity just to report a count. Reused across the
 * country/device/browser/OS breakdown queries in {@link
 * com.shortx.repository.ClickEventRepository} since they all share this
 * exact shape.
 */
public interface BreakdownCount {
    String getKey();
    Long getCount();
}
