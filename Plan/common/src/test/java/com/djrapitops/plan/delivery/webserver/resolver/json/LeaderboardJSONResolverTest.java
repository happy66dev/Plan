/*
 *  This file is part of Player Analytics (Plan).
 *
 *  Plan is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License v3 as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Plan is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Plan. If not, see <https://www.gnu.org/licenses/>.
 */
package com.djrapitops.plan.delivery.webserver.resolver.json;

import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.URIQuery;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.identification.Identifiers;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.Database;
import com.djrapitops.plan.storage.database.queries.analysis.TopListQueries;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * 验证 {@link LeaderboardJSONResolver} 参数校验与权限判断的单元测试喵~
 * <p>
 * 用 mock 隔离数据库,聚焦参数防御:缺 field、非法 field、扩展缺 plugin、非法 limit,
 * 以及 canAccess 的权限分支。
 *
 * @author AuroraLS3
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardJSONResolverTest {

    // mock 数据库系统与标识符解析器,隔离真实数据库
    @Mock
    DBSystem dbSystem;
    @Mock
    Database database;
    @Mock
    Identifiers identifiers;
    @Mock
    Request request;

    // 被测的排行榜解析器
    LeaderboardJSONResolver resolver;

    @BeforeEach
    void setUp() {
        // 手工构造被测对象,注入 mock 依赖
        resolver = new LeaderboardJSONResolver(dbSystem, identifiers);
    }

    @Test
    void missingFieldThrowsBadRequest() {
        // 没有任何 query 参数,field 必填缺失,应抛 400
        when(request.getQuery()).thenReturn(new URIQuery(Map.of()));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
    }

    @Test
    void unknownFieldThrowsBadRequest() {
        // field 不是任何内置指标名,也不是 extension: 前缀,应抛 400
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("field", "not-a-metric")));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
    }

    @Test
    void extensionFieldWithoutPluginThrowsBadRequest() {
        // 扩展字段必须带 plugin 消歧,缺失时抛 400
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("field", "extension:level")));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
    }

    @Test
    void invalidLimitThrowsBadRequest() {
        // limit 不是数字时抛 400
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("field", "playtime", "limit", "abc")));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
    }

    @Test
    void invalidTimeParameterThrowsBadRequest() {
        // after 不是数字时抛 400
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("field", "playtime", "after", "not-a-time")));
        assertThrows(BadRequestException.class, () -> resolver.resolve(request));
    }

    @Test
    void resolvesBuiltInMetricLeaderboard() {
        // 正常内置指标请求:mock 数据库返回两条排行榜条目
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("field", "playtime", "limit", "2")));
        when(dbSystem.getDatabase()).thenReturn(database);
        when(database.query(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new TopListQueries.TopListEntry<>("PlayerA", 1000L),
                new TopListQueries.TopListEntry<>("PlayerB", 500L)));

        Optional<Response> result = resolver.resolve(request);
        // 应正常返回响应
        assertTrue(result.isPresent());
        assertEquals(200, result.get().getCode());
        // 解析响应 JSON,断言字段与 entries 结构
        Map<?, ?> json = new Gson().fromJson(result.get().getAsString(), Map.class);
        assertEquals("playtime", json.get("field"));
        assertEquals(null, json.get("server"));
        assertEquals(null, json.get("plugin"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) json.get("entries");
        assertEquals(2, entries.size());
        assertEquals("PlayerA", entries.get(0).get("name"));
        assertEquals(1000.0, ((Number) entries.get(0).get("value")).doubleValue());
        assertEquals("PlayerB", entries.get(1).get("name"));
        assertEquals(500.0, ((Number) entries.get(1).get("value")).doubleValue());
    }

    @Test
    void networkScopeRequiresNetworkPermission() {
        // 不带 server 参数,属于网络级数据,拥有 access.network 即可访问
        when(request.getQuery()).thenReturn(new URIQuery(Map.of()));
        when(request.getUser()).thenReturn(Optional.of(new WebUser("", "", Set.of("access.network"))));
        assertTrue(resolver.canAccess(request));
    }

    @Test
    void networkScopeWithoutNetworkPermissionIsDenied() {
        // 不带 server 参数但只有 access.server 权限,应被拒绝
        when(request.getQuery()).thenReturn(new URIQuery(Map.of()));
        when(request.getUser()).thenReturn(Optional.of(new WebUser("", "", Set.of("access.server"))));
        assertFalse(resolver.canAccess(request));
    }

    @Test
    void serverScopeRequiresServerPermission() {
        // 带 server 参数,属于服务器级数据,拥有 access.server 即可访问
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("server", "Server 1")));
        when(request.getUser()).thenReturn(Optional.of(new WebUser("", "", Set.of("access.server"))));
        assertTrue(resolver.canAccess(request));
    }

    @Test
    void serverScopeWithoutServerPermissionIsDenied() {
        // 带 server 参数但只有 access.network 权限,应被拒绝
        when(request.getQuery()).thenReturn(new URIQuery(Map.of("server", "Server 1")));
        when(request.getUser()).thenReturn(Optional.of(new WebUser("", "", Set.of("access.network"))));
        assertFalse(resolver.canAccess(request));
    }

    @Test
    void missingUserFallsBackToNoPermissions() {
        // 未登录用户(无 WebUser)以空权限兜底,默认不能访问带权限要求的接口
        when(request.getQuery()).thenReturn(new URIQuery(Map.of()));
        when(request.getUser()).thenReturn(Optional.empty());
        assertFalse(resolver.canAccess(request));
    }
}
