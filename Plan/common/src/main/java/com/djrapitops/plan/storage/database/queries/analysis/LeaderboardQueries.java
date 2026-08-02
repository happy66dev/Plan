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
package com.djrapitops.plan.storage.database.queries.analysis;

import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.KillsTable;
import com.djrapitops.plan.storage.database.sql.tables.PingTable;
import com.djrapitops.plan.storage.database.sql.tables.ServerTable;
import com.djrapitops.plan.storage.database.sql.tables.SessionsTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * 为排行榜 API 提供"按指定字段取全服 Top N 玩家"的查询集合喵~
 * <p>
 * 与 {@link TopListQueries} 的区别:这里 limit 可参数化(任意数量),而非写死 Top 10。
 * 复用 {@link TopListQueries.TopListEntry} 作为单条返回结构。
 *
 * @author AuroraLS3
 */
public class LeaderboardQueries {

    // 私有构造,防止实例化(本类全部为静态工厂方法)喵~
    private LeaderboardQueries() {
        /* Static method class */
    }

    /**
     * 全服游戏时长排行榜喵~ playtime = 会话时长之和。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒),用于过滤会话
     * @param before 时间窗终点(毫秒),用于过滤会话
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> playtimeLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 把 playtime 的聚合表达式传入通用会话聚合查询
        return sessionAggregateLeaderboard("SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ')', server, limit, after, before);
    }

    /**
     * 全服活跃游戏时长排行榜喵~ active_playtime = 会话时长之和减挂机时长。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> activePlaytimeLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 把 active_playtime 的聚合表达式传入通用会话聚合查询
        return sessionAggregateLeaderboard("SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + '-' + SessionsTable.AFK_TIME + ')', server, limit, after, before);
    }

    /**
     * 全服挂机时长排行榜喵~ afk_time = 会话挂机时长之和。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> afkTimeLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 把 afk_time 的聚合表达式传入通用会话聚合查询
        return sessionAggregateLeaderboard("SUM(" + SessionsTable.AFK_TIME + ')', server, limit, after, before);
    }

    /**
     * 全服会话次数排行榜喵~ session_count = 会话行数。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> sessionCountLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 把会话计数的聚合表达式传入通用会话聚合查询
        return sessionAggregateLeaderboard("COUNT(1)", server, limit, after, before);
    }

    /**
     * 全服怪物击杀数排行榜喵~ mob_kill_count = 会话 mob_kills 之和。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> mobKillCountLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 把怪物击杀数的聚合表达式传入通用会话聚合查询
        return sessionAggregateLeaderboard("SUM(" + SessionsTable.MOB_KILLS + ')', server, limit, after, before);
    }

    /**
     * 全服死亡次数排行榜喵~ death_count = 会话 deaths 之和。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> deathCountLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 把死亡次数的聚合表达式传入通用会话聚合查询
        return sessionAggregateLeaderboard("SUM(" + SessionsTable.DEATHS + ')', server, limit, after, before);
    }

    /**
     * 全服最大 Ping 排行榜喵~ max_ping = PingTable.max_ping 的最大值。
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> maxPingLeaderboard(ServerUUID server, int limit, long after, long before) {
        String serverFilter = server != null ? WHERE + "se." + ServerTable.SERVER_UUID + "=?" + AND : WHERE;
        String sql = SELECT + "u." + UsersTable.USER_NAME + ", MAX(p." + PingTable.MAX_PING + ") as val" +
                FROM + PingTable.TABLE_NAME + " p" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=p." + PingTable.USER_ID +
                INNER_JOIN + ServerTable.TABLE_NAME + " se on se." + ServerTable.ID + "=p." + PingTable.SERVER_ID +
                serverFilter + "p." + PingTable.DATE + ">?" + AND + "p." + PingTable.DATE + "<?" +
                GROUP_BY + "u." + UsersTable.USER_NAME + ORDER_BY + "val DESC" + LIMIT + "?";
        return new QueryStatement<List<TopListQueries.TopListEntry<Long>>>(sql, limit) {
            @Override public void prepare(PreparedStatement statement) throws SQLException {
                int parameterIndex = 1;
                if (server != null) statement.setString(parameterIndex++, server.toString());
                statement.setLong(parameterIndex++, after); statement.setLong(parameterIndex++, before); statement.setInt(parameterIndex, limit);
            }
            @Override public List<TopListQueries.TopListEntry<Long>> processResults(ResultSet set) throws SQLException {
                List<TopListQueries.TopListEntry<Long>> entries = new ArrayList<>();
                while (set.next()) entries.add(new TopListQueries.TopListEntry<>(set.getString(UsersTable.USER_NAME), set.getLong("val")));
                return entries;
            }
        };
    }

    /** 玩家 KDR 排行榜喵~ kills 除以 sessions,分母为零的玩家不返回。 */
    public static Query<List<TopListQueries.TopListEntry<Double>>> playerKdrLeaderboard(ServerUUID server, int limit, long after, long before) {
        return ratioLeaderboard(true, server, limit, after, before);
    }

    /** 怪物 KDR 排行榜喵~ mob_kills 除以 sessions,分母为零的玩家不返回。 */
    public static Query<List<TopListQueries.TopListEntry<Double>>> mobKdrLeaderboard(ServerUUID server, int limit, long after, long before) {
        return ratioLeaderboard(false, server, limit, after, before);
    }

    private static Query<List<TopListQueries.TopListEntry<Double>>> ratioLeaderboard(boolean playerKills, ServerUUID server, int limit, long after, long before) {
        String numerator = playerKills ? "COUNT(DISTINCT k." + KillsTable.ID + ")" : "SUM(s." + SessionsTable.MOB_KILLS + ")";
        String killJoin = playerKills
                ? LEFT_JOIN + KillsTable.TABLE_NAME + " k on k." + KillsTable.KILLER_UUID + "=u." + UsersTable.USER_UUID + AND + "k." + KillsTable.DATE + ">?" + AND + "k." + KillsTable.DATE + "<?"
                : "";
        String sql = SELECT + "u." + UsersTable.USER_NAME + ", (" + numerator + " * 1.0 / COUNT(DISTINCT s." + SessionsTable.ID + ")) as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID + killJoin +
                LEFT_JOIN + ServerTable.TABLE_NAME + " se on se." + ServerTable.ID + "=s." + SessionsTable.SERVER_ID +
                (server != null ? WHERE + "se." + ServerTable.SERVER_UUID + "=?" + AND : WHERE) +
                "s." + SessionsTable.SESSION_START + ">?" + AND + "s." + SessionsTable.SESSION_END + "<?" +
                GROUP_BY + "u." + UsersTable.USER_NAME + ORDER_BY + "val DESC" + LIMIT + "?";
        return new QueryStatement<List<TopListQueries.TopListEntry<Double>>>(sql, limit) {
            @Override public void prepare(PreparedStatement statement) throws SQLException {
                int parameterIndex = 1;
                if (playerKills) { statement.setLong(parameterIndex++, after); statement.setLong(parameterIndex++, before); }
                if (server != null) statement.setString(parameterIndex++, server.toString());
                statement.setLong(parameterIndex++, after); statement.setLong(parameterIndex++, before); statement.setInt(parameterIndex, limit);
            }
            @Override public List<TopListQueries.TopListEntry<Double>> processResults(ResultSet set) throws SQLException {
                List<TopListQueries.TopListEntry<Double>> entries = new ArrayList<>();
                while (set.next()) entries.add(new TopListQueries.TopListEntry<>(set.getString(UsersTable.USER_NAME), set.getDouble("val")));
                return entries;
            }
        };
    }

    /**
     * 全服玩家击杀数排行榜喵~ player_kill_count = kills 表中 killer 是该玩家的行数。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> playerKillCountLeaderboard(ServerUUID server, int limit, long after, long before) {
        // 击杀记录来自 kills 表,按 killer 分组计数;可选的服务器过滤
        String serverWhere = server != null ? WHERE + KillsTable.SERVER_UUID + "=?" + AND : "";
        // 拼接:按击杀时间窗过滤,再按玩家名分组排序
        String sql = SELECT + "u." + UsersTable.USER_NAME + ", COUNT(1) as val" +
                FROM + KillsTable.TABLE_NAME + " k" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.USER_UUID + "=k." + KillsTable.KILLER_UUID +
                serverWhere + KillsTable.DATE + ">?" + AND + KillsTable.DATE + "<?" +
                GROUP_BY + "u." + UsersTable.USER_NAME +
                ORDER_BY + "val DESC" + LIMIT + "?";
        // 返回带可选服务器过滤与 limit 保护的查询
        return new QueryStatement<List<TopListQueries.TopListEntry<Long>>>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                // 依次绑定:服务器 UUID(如有)、时间窗起止、limit
                int i = 1;
                if (server != null) {
                    statement.setString(i++, server.toString());
                }
                statement.setLong(i++, after);
                statement.setLong(i++, before);
                statement.setInt(i, limit);
            }

            @Override
            public List<TopListQueries.TopListEntry<Long>> processResults(ResultSet set) throws SQLException {
                // 遍历结果集,逐个玩家组装排行榜条目
                List<TopListQueries.TopListEntry<Long>> entries = new ArrayList<>();
                while (set.next()) {
                    entries.add(new TopListQueries.TopListEntry<>(set.getString(UsersTable.USER_NAME), set.getLong("val")));
                }
                return entries;
            }
        };
    }

    /**
     * 全服踢出次数排行榜喵~ kick_count = plan_users.times_kicked,无时间窗。
     *
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     * @return 排行榜查询对象
     */
    public static Query<List<TopListQueries.TopListEntry<Long>>> kickCountLeaderboard(ServerUUID server, int limit) {
        // 踢出次数在 users 表,按玩家排序即可;踢出属于玩家全局属性,不区分服务器
        String sql = SELECT + UsersTable.USER_NAME + ", " + UsersTable.TIMES_KICKED + " as val" +
                FROM + UsersTable.TABLE_NAME +
                ORDER_BY + "val DESC" + LIMIT + "?";
        // 返回带 limit 保护的查询(踢出次数与时间窗/服务器无关,忽略对应参数)
        return new QueryStatement<List<TopListQueries.TopListEntry<Long>>>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                // 只绑定 limit 一个参数
                statement.setInt(1, limit);
            }

            @Override
            public List<TopListQueries.TopListEntry<Long>> processResults(ResultSet set) throws SQLException {
                // 遍历结果集,逐个玩家组装排行榜条目
                List<TopListQueries.TopListEntry<Long>> entries = new ArrayList<>();
                while (set.next()) {
                    entries.add(new TopListQueries.TopListEntry<>(set.getString(UsersTable.USER_NAME), set.getLong("val")));
                }
                return entries;
            }
        };
    }

    /**
     * 通用"基于会话表聚合"的排行榜查询喵~ 会话类的指标(时长/次数/击杀/死亡)共用此方法。
     *
     * @param valueExpression 该指标的 SQL 聚合表达式(如 SUM(...) 或 COUNT(1))
     * @param server 目标服务器 UUID,为 null 表示全网络(不联服务器表过滤)
     * @param limit 返回条数上限
     * @param after 时间窗起点(毫秒)
     * @param before 时间窗终点(毫秒)
     * @return 排行榜查询对象
     */
    private static Query<List<TopListQueries.TopListEntry<Long>>> sessionAggregateLeaderboard(
            String valueExpression, ServerUUID server, int limit, long after, long before
    ) {
        // 需要过滤服务器时才关联服务器表,否则不关联
        String serverJoin = server != null
                ? LEFT_JOIN + ServerTable.TABLE_NAME + " se on se." + ServerTable.ID + "=s." + SessionsTable.SERVER_ID
                : "";
        // 拼 WHERE:有服务器先过滤服务器,再过滤时间窗;无服务器直接过滤时间窗
        String where = server != null
                ? WHERE + "se." + ServerTable.SERVER_UUID + "=?" + AND + SessionsTable.SESSION_START + ">?" + AND + SessionsTable.SESSION_END + "<?"
                : WHERE + SessionsTable.SESSION_START + ">?" + AND + SessionsTable.SESSION_END + "<?";
        // 拼接完整查询:按玩家名分组,按指标值降序,限制条数
        String sql = SELECT + "u." + UsersTable.USER_NAME + ", " + valueExpression + " as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                serverJoin + where +
                GROUP_BY + "u." + UsersTable.USER_NAME +
                ORDER_BY + "val DESC" + LIMIT + "?";
        // 返回带服务器/时间窗过滤与 limit 保护的查询
        return new QueryStatement<List<TopListQueries.TopListEntry<Long>>>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                // 依次绑定:服务器 UUID(如有)、时间窗起止、limit
                int i = 1;
                if (server != null) {
                    statement.setString(i++, server.toString());
                }
                statement.setLong(i++, after);
                statement.setLong(i++, before);
                statement.setInt(i, limit);
            }

            @Override
            public List<TopListQueries.TopListEntry<Long>> processResults(ResultSet set) throws SQLException {
                // 遍历结果集,逐个玩家组装排行榜条目
                List<TopListQueries.TopListEntry<Long>> entries = new ArrayList<>();
                while (set.next()) {
                    entries.add(new TopListQueries.TopListEntry<>(set.getString(UsersTable.USER_NAME), set.getLong("val")));
                }
                return entries;
            }
        };
    }
}
