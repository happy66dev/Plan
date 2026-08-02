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
package com.djrapitops.plan.delivery.webserver.resolver.json.leaderboard;

import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.analysis.LeaderboardQueries;
import com.djrapitops.plan.storage.database.queries.analysis.TopListQueries;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 排行榜字段分派注册表喵~ 负责把排行榜 API 的 field 参数映射到对应的内置指标查询,
 * 并识别第三方插件的扩展数据列(extension: 前缀)。
 *
 * @author AuroraLS3
 */
public final class LeaderboardMetricRegistry {

    /** field 参数中第三方插件数据列的前缀,如 extension:等级 喵~ */
    public static final String EXTENSION_PREFIX = "extension:";

    // 私有构造,防止实例化(本类全部为静态成员)喵~
    private LeaderboardMetricRegistry() {
        /* Static method class */
    }

    /**
     * 判断 field 是否指向第三方插件的扩展数据列喵~
     *
     * @param field 排行榜 field 参数
     * @return 以 extension: 开头则为 true
     */
    public static boolean isExtensionField(String field) {
        return field != null && field.startsWith(EXTENSION_PREFIX);
    }

    /**
     * 从 extension: 前缀的 field 中剥离出真正的 provider 数据列名喵~
     *
     * @param field 排行榜 field 参数(形如 extension:等级)
     * @return 剥离前缀后的 provider 名(形如 等级)
     */
    public static String extensionProviderName(String field) {
        return field.substring(EXTENSION_PREFIX.length());
    }

    /**
     * 内置可排名指标的枚举喵~ 每个枚举值对应一个内置排行榜查询工厂。
     */
    public enum BuiltInMetric {

        // 各枚举值绑定一个可排名指标名,以及对应的排行榜查询工厂方法
        PLAYTIME("playtime", LeaderboardQueries::playtimeLeaderboard),
        ACTIVE_PLAYTIME("active_playtime", LeaderboardQueries::activePlaytimeLeaderboard),
        AFK_TIME("afk_time", LeaderboardQueries::afkTimeLeaderboard),
        SESSION_COUNT("session_count", LeaderboardQueries::sessionCountLeaderboard),
        MOB_KILL_COUNT("mob_kill_count", LeaderboardQueries::mobKillCountLeaderboard),
        DEATH_COUNT("death_count", LeaderboardQueries::deathCountLeaderboard),
        PLAYER_KILL_COUNT("player_kill_count", LeaderboardQueries::playerKillCountLeaderboard),
        MAX_PING("max_ping", LeaderboardQueries::maxPingLeaderboard),
        PLAYER_KDR("player_kdr", LeaderboardQueries::playerKdrLeaderboard),
        MOB_KDR("mob_kdr", LeaderboardQueries::mobKdrLeaderboard),
        // 踢出次数不区分时间窗与服务器,用 lambda 忽略对应参数
        BLOCK_BREAK_COUNT("block_break_count", LeaderboardQueries::blockBreakCountLeaderboard),
        BLOCK_PLACE_COUNT("block_place_count", LeaderboardQueries::blockPlaceCountLeaderboard),
        KICK_COUNT("kick_count", (server, limit, after, before) -> LeaderboardQueries.kickCountLeaderboard(server, limit));

        private final String name;                       // field 参数使用的指标名(小写)
        private final LeaderboardQueryFactory queryFactory; // 构建该指标排行榜查询的工厂

        /**
         * 构造内置指标枚举项喵~
         *
         * @param name field 参数使用的指标名
         * @param queryFactory 构建排行榜查询的工厂
         */
        BuiltInMetric(String name, LeaderboardQueryFactory queryFactory) {
            this.name = name;
            this.queryFactory = queryFactory;
        }

        /**
         * 获取 field 参数使用的指标名喵~
         *
         * @return 指标名(小写)
         */
        public String getName() {
            return name;
        }

        /**
         * 构建该指标的排行榜查询喵~
         *
         * @param server 目标服务器 UUID,为 null 表示全网络
         * @param limit 返回条数上限
         * @param after 时间窗起点(毫秒)
         * @param before 时间窗终点(毫秒)
         * @return 排行榜查询对象
         */
        public Query<? extends List<? extends TopListQueries.TopListEntry<? extends Number>>> createQuery(ServerUUID server, int limit, long after, long before) {
            return queryFactory.create(server, limit, after, before);
        }

        /**
         * 根据 field 指标名查找对应的内置指标枚举喵~ 忽略大小写。
         *
         * @param field 排行榜 field 参数
         * @return 匹配的内置指标枚举,未找到则返回空
         */
        public static Optional<BuiltInMetric> fromName(String field) {
            // 遍历所有内置指标,按名字(忽略大小写)匹配
            return Arrays.stream(values())
                    .filter(metric -> metric.name.equalsIgnoreCase(field))
                    .findFirst();
        }
    }

    /**
     * 内置排行榜查询的工厂接口喵~ 各内置指标对应一个实现。
     */
    @FunctionalInterface
    public interface LeaderboardQueryFactory {

        /**
         * 构建排行榜查询喵~
         *
         * @param server 目标服务器 UUID,为 null 表示全网络
         * @param limit 返回条数上限
         * @param after 时间窗起点(毫秒)
         * @param before 时间窗终点(毫秒)
         * @return 排行榜查询对象
         */
        Query<? extends List<? extends TopListQueries.TopListEntry<? extends Number>>> create(ServerUUID server, int limit, long after, long before);
    }
}
