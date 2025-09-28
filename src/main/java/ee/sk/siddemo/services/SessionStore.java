package ee.sk.siddemo.services;

/*-
 * #%L
 * Smart-ID sample Java client
 * %%
 * Copyright (C) 2018 - 2025 SK ID Solutions AS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import net.jodah.expiringmap.ExpiringMap;

@Service
public class SessionStore {

    private final ExpiringMap<String, ConcurrentHashMap<String,Object>> expiringMap =
            ExpiringMap.builder()
                    .expiration(5, TimeUnit.MINUTES)   // align with your session timeout
                    .variableExpiration()
                    .build();

    /** Set/replace a single attribute */
    public void put(String sessionId, String name, Object value) {
        expiringMap.compute(sessionId, (sid, attrs) -> {
            if (attrs == null) attrs = new ConcurrentHashMap<>();
            attrs.put(name, value);
            return attrs; // writing refreshes TTL
        });
    }

    /** Get a single attribute */
    public Object get(String sessionId, String name) {
        var attrs = expiringMap.get(sessionId);
        return attrs != null ? attrs.get(name) : null;
    }

    /** Get all attributes for a session (what values it holds) */
    public Map<String,Object> all(String sessionId) {
        var attrs = expiringMap.get(sessionId);
        return attrs != null ? Map.copyOf(attrs) : Map.of();
    }

    /** Remove an attribute */
    public void remove(String sessionId, String name) {
        var attrs = expiringMap.get(sessionId);
        if (attrs != null) attrs.remove(name);
    }

    /** Remove whole session snapshot (e.g., on sessionDestroyed) */
    public void remove(String sessionId) { expiringMap.remove(sessionId); }

    /** Query: which sessionIds have attr present? (simple scan) */
    public java.util.List<String> having(String name) {
        return expiringMap.entrySet().stream()
                .filter(e -> e.getValue().containsKey(name))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Query: which sessionIds have attr==value? (simple scan) */
    public java.util.List<String> find(String name, Object value) {
        return expiringMap.entrySet().stream()
                .filter(e -> value.equals(e.getValue().get(name)))
                .map(Map.Entry::getKey)
                .toList();
    }
}
