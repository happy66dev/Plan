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

import com.djrapitops.plan.delivery.domain.auth.WebPermission;
import com.djrapitops.plan.delivery.web.resolver.MimeType;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.BadRequestException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.URIQuery;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import com.djrapitops.plan.delivery.webserver.resolver.json.leaderboard.LeaderboardMetricRegistry;
import com.djrapitops.plan.extension.implementation.storage.queries.ExtensionLeaderboardQuery;
import com.djrapitops.plan.identification.Identifiers;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.DBSystem;
import com.djrapitops.plan.storage.database.queries.analysis.TopListQueries;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 排行榜 API 的解析器喵~ 对应 GET /v1/leaderboard。
 * <p>
 * 按请求动态指定字段返回全服 Top N 玩家,支持内置指标与第三方插件扩展数据列。
 *
 * @author AuroraLS3
 */
@Singleton
@Path("/v1/leaderboard")
public class LeaderboardJSONResolver implements Resolver {

    private final DBSystem dbSystem;   // 数据库访问入口,用于执行排行榜查询
    private final Identifiers identifiers; // 解析服务器等标识符

    /**
     * 依赖注入构造器喵~
     *
     * @param dbSystem 数据库系统
     * @param identifiers 标识符解析工具
     */
    @Inject
    public LeaderboardJSONResolver(DBSystem dbSystem, Identifiers identifiers) {
        this.dbSystem = dbSystem;
        this.identifiers = identifiers;
    }

    @Override
    public boolean canAccess(Request request) {
        // 未登录用户用空权限兜底(认证关闭时不经过此方法)
        WebUser user = request.getUser().orElse(new WebUser(""));
        // 带 server 参数属于服务器级数据,否则属于网络级数据,按此选择所需权限
        WebPermission permission = request.getQuery().get("server").isPresent()
                ? WebPermission.ACCESS_SERVER
                : WebPermission.ACCESS_NETWORK;
        // 返回该用户是否拥有对应权限
        return user.hasPermission(permission);
    }

    @GET
    @Operation(
            description = "Get a leaderboard of players by a built-in metric or a third-party extension data column",
            responses = {
                    @ApiResponse(responseCode = "200", content = @Content(mediaType = MimeType.JSON)),
                    @ApiResponse(responseCode = "400", description = "If 'field' is missing/unknown, or an extension field lacks 'plugin', or parameters are invalid")
            },
            parameters = {
                    @Parameter(in = ParameterIn.QUERY, name = "field", description = "Built-in metric name (playtime, active_playtime, afk_time, session_count, mob_kill_count, death_count, player_kill_count, max_ping, player_kdr, mob_kdr, kick_count) or 'extension:<providerName>'", examples = {
                            @ExampleObject("playtime"),
                            @ExampleObject("extension:level"),
                    }),
                    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "Max number of entries (default 10, clamped to 1-100)", example = "10"),
                    @Parameter(in = ParameterIn.QUERY, name = "server", description = "Server identifier to scope the leaderboard (optional)", examples = {
                            @ExampleObject("Server 1"),
                            @ExampleObject("1fb39d2a-eb82-4868-b245-1fad17d823b3"),
                    }),
                    @Parameter(in = ParameterIn.QUERY, name = "plugin", description = "Plugin name to disambiguate an extension field (required for extension: fields)", example = "SomePlugin"),
                    @Parameter(in = ParameterIn.QUERY, name = "after", description = "Time window start in epoch millis (optional, defaults to all time)", example = "1700000000000"),
                    @Parameter(in = ParameterIn.QUERY, name = "before", description = "Time window end in epoch millis (optional, defaults to all time)", example = "1722441600000"),
            },
            requestBody = @RequestBody(content = @Content(examples = @ExampleObject()))
    )
    @Override
    public Optional<Response> resolve(Request request) {
        // 读取并校验 query 参数
        URIQuery query = request.getQuery();
        String field = query.get("field")
                .orElseThrow(() -> new BadRequestException("'field' is required"));
        int limit = parseLimit(query.get("limit"));
        Optional<ServerUUID> server = parseServer(query.get("server"));
        long after = parseTime(query.get("after"), "after").orElse(Long.MIN_VALUE);
        long before = parseTime(query.get("before"), "before").orElse(Long.MAX_VALUE);

        // 根据 field 是否扩展前缀,分派到扩展查询或内置查询
        List<TopListQueries.TopListEntry<? extends Number>> entries = new ArrayList<>();
        String plugin = null;
        if (LeaderboardMetricRegistry.isExtensionField(field)) {
            // 扩展数据列:剥离前缀得到 provider 名,且必须提供 plugin 消歧
            String provider = LeaderboardMetricRegistry.extensionProviderName(field);
            plugin = query.get("plugin")
                    .orElseThrow(() -> new BadRequestException("'plugin' is required for extension fields"));
            entries.addAll(dbSystem.getDatabase().query(
                    new ExtensionLeaderboardQuery(provider, plugin, server.orElse(null), limit)));
        } else {
            // 内置指标:按名字找到对应枚举,构建排行榜查询
            LeaderboardMetricRegistry.BuiltInMetric metric = LeaderboardMetricRegistry.BuiltInMetric.fromName(field)
                    .orElseThrow(() -> new BadRequestException("Unknown field: " + field));
            entries.addAll(dbSystem.getDatabase().query(metric.createQuery(server.orElse(null), limit, after, before)));
        }

        // 组装响应 JSON
        Map<String, Object> json = new HashMap<>();
        json.put("field", field);
        json.put("server", server.map(ServerUUID::toString).orElse(null));
        json.put("plugin", plugin);
        // 把每个排行榜条目转成 name/value 对
        json.put("entries", entries.stream().map(entry -> {
            Map<String, Object> e = new HashMap<>();
            e.put("name", entry.getPlayerName());
            e.put("value", entry.getValue());
            return e;
        }).collect(Collectors.toList()));

        // 返回 JSON 响应
        return Optional.of(Response.builder()
                .setMimeType(MimeType.JSON)
                .setJSONContent(json)
                .build());
    }

    /**
     * 解析并限制 limit 参数喵~ 缺失时默认 10,超出 [1,100] 时收敛到边界。
     *
     * @param limit 原始 limit 参数
     * @return 收敛后的条数上限
     */
    private int parseLimit(Optional<String> limit) {
        // 喵~防御:非数字的 limit 抛 400
        int parsed = limit
                .map(value -> {
                    try {
                        return Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new BadRequestException("'limit' must be a number");
                    }
                })
                .orElse(10);
        // 喵~防御:限制返回条数在 [1,100],避免单次返回海量数据拖垮服务
        return Math.max(1, Math.min(100, parsed));
    }

    /**
     * 解析 server 参数喵~ 有值则解析为 ServerUUID,非法/不存在抛 400。
     *
     * @param server 原始 server 参数
     * @return 可选的服务器 UUID
     */
    private Optional<ServerUUID> parseServer(Optional<String> server) {
        // 喵~防御:提供的服务器在数据库中找不到时抛 400
        return server.map(identifier -> identifiers.getServerUUID(identifier)
                .orElseThrow(() -> new BadRequestException("Given 'server' was not found in the database.")));
    }

    /**
     * 解析时间窗参数喵~ 有值则解析为 long,非数字抛 400。
     *
     * @param value 原始时间参数
     * @param paramName 参数名,用于错误信息
     * @return 可选的 long 时间戳(毫秒)
     */
    private Optional<Long> parseTime(Optional<String> value, String paramName) {
        // 喵~防御:非数字的时间窗参数抛 400
        return value.map(raw -> {
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException e) {
                throw new BadRequestException("'" + paramName + "' must be a number");
            }
        });
    }
}
