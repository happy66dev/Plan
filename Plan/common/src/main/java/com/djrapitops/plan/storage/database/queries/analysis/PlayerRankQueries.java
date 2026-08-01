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

import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.sql.tables.KillsTable;
import com.djrapitops.plan.storage.database.sql.tables.SessionsTable;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * 为 /v1/player 提供"全服排名"的查询集合喵~
 * <p>
 * 排名口径:全服终身累计,与 /v1/player 的 info 展示口径保持一致。
 * 排名规则:rank = 全服指标值比当前玩家大的玩家数量 + 1(标准 competition rank,并列共享名次)。
 *
 * @author AuroraLS3
 */
public class PlayerRankQueries {

    // 私有构造,防止实例化(本类全部为静态工厂方法)喵~
    private PlayerRankQueries() {
        /* Static method class */
    }

    /**
     * 查询玩家在全服的游戏时长排名喵~ playtime = 所有会话时长之和。
     *
     * @param playerUUID 玩家 UUID,唯一标识一个玩家
     * @return 查询对象,执行后得到可选的排名结果(玩家无会话时为空)
     */
    public static Query<Optional<PlayerRank>> playtimeRank(UUID playerUUID) {
        // 全服所有玩家的 playtime 聚合结果(按玩家分组)
        String allValuesSql = SELECT + "s." + SessionsTable.USER_ID + ", " +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                GROUP_BY + "s." + SessionsTable.USER_ID;
        // 指定玩家的 playtime 聚合结果(单行)
        String playerValueSql = SELECT + "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的活跃游戏时长排名喵~ active_playtime = 会话时长之和再减去挂机(afk)时长。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> activePlaytimeRank(UUID playerUUID) {
        // 全服 active_playtime 聚合
        String allValuesSql = SELECT + "s." + SessionsTable.USER_ID + ", " +
                "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + '-' + SessionsTable.AFK_TIME + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                GROUP_BY + "s." + SessionsTable.USER_ID;
        // 指定玩家 active_playtime 聚合
        String playerValueSql = SELECT + "SUM(" + SessionsTable.SESSION_END + '-' + SessionsTable.SESSION_START + '-' + SessionsTable.AFK_TIME + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的挂机时长排名喵~ afk_time = 所有会话挂机时长之和。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> afkTimeRank(UUID playerUUID) {
        // 全服 afk_time 聚合
        String allValuesSql = SELECT + "s." + SessionsTable.USER_ID + ", " +
                "SUM(" + SessionsTable.AFK_TIME + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                GROUP_BY + "s." + SessionsTable.USER_ID;
        // 指定玩家 afk_time 聚合
        String playerValueSql = SELECT + "SUM(" + SessionsTable.AFK_TIME + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的会话次数排名喵~ session_count = 会话行数。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> sessionCountRank(UUID playerUUID) {
        // 全服会话次数聚合
        String allValuesSql = SELECT + "s." + SessionsTable.USER_ID + ", " +
                "COUNT(1) as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                GROUP_BY + "s." + SessionsTable.USER_ID;
        // 指定玩家会话次数聚合
        String playerValueSql = SELECT + "COUNT(1) as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的怪物击杀数排名喵~ mob_kill_count = 所有会话 mob_kills 之和。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> mobKillCountRank(UUID playerUUID) {
        // 全服怪物击杀数聚合
        String allValuesSql = SELECT + "s." + SessionsTable.USER_ID + ", " +
                "SUM(" + SessionsTable.MOB_KILLS + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                GROUP_BY + "s." + SessionsTable.USER_ID;
        // 指定玩家怪物击杀数聚合
        String playerValueSql = SELECT + "SUM(" + SessionsTable.MOB_KILLS + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的死亡次数排名喵~ death_count = 所有会话 deaths 之和(含怪物与玩家造成的死亡)。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> deathCountRank(UUID playerUUID) {
        // 全服死亡次数聚合
        String allValuesSql = SELECT + "s." + SessionsTable.USER_ID + ", " +
                "SUM(" + SessionsTable.DEATHS + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                GROUP_BY + "s." + SessionsTable.USER_ID;
        // 指定玩家死亡次数聚合
        String playerValueSql = SELECT + "SUM(" + SessionsTable.DEATHS + ") as val" +
                FROM + SessionsTable.TABLE_NAME + " s" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.ID + "=s." + SessionsTable.USER_ID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的玩家击杀数排名喵~ player_kill_count = plan_kills 中 killer 是该玩家的行数。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> playerKillCountRank(UUID playerUUID) {
        // 全服玩家击杀数聚合(击杀记录来自 kills 表,按 killer 分组)
        String allValuesSql = SELECT + "u." + UsersTable.ID + ", " +
                "COUNT(1) as val" +
                FROM + KillsTable.TABLE_NAME + " k" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.USER_UUID + "=k." + KillsTable.KILLER_UUID +
                GROUP_BY + "u." + UsersTable.ID;
        // 指定玩家玩家击杀数聚合
        String playerValueSql = SELECT + "COUNT(1) as val" +
                FROM + KillsTable.TABLE_NAME + " k" +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.USER_UUID + "=k." + KillsTable.KILLER_UUID +
                WHERE + "u." + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 查询玩家在全服的踢出次数排名喵~ kick_count = plan_users.times_kicked。
     *
     * @param playerUUID 玩家 UUID
     * @return 查询对象,可选排名结果
     */
    public static Query<Optional<PlayerRank>> kickCountRank(UUID playerUUID) {
        // 全服踢出次数聚合(直接来自 users 表,无需关联会话表)
        String allValuesSql = SELECT + UsersTable.ID + ", " +
                "MAX(" + UsersTable.TIMES_KICKED + ") as val" +
                FROM + UsersTable.TABLE_NAME +
                GROUP_BY + UsersTable.ID;
        // 指定玩家踢出次数聚合
        String playerValueSql = SELECT + "MAX(" + UsersTable.TIMES_KICKED + ") as val" +
                FROM + UsersTable.TABLE_NAME +
                WHERE + UsersTable.USER_UUID + "=?";
        // 用通用排名 SQL 生成查询
        return buildRankQuery(allValuesSql, playerValueSql, playerUUID);
    }

    /**
     * 用通用 SQL 模板构建排名查询喵~ 核心思路:
     * 1. 子查询 {@code me} 算当前玩家的指标值
     * 2. 子查询 {@code allValues} 算全服所有玩家的指标值
     * 3. rank = 全服值比当前玩家大的玩家数 + 1;total = 全服有该指标值的玩家数
     * 若当前玩家没有该指标值(me 的 val 为 NULL),返回空,避免出现 0/0 或误排名。
     *
     * @param allValuesSql 全服玩家聚合 SQL,需返回 user_id 与 val 两列
     * @param playerValueSql 指定玩家聚合 SQL,需返回 val 一列
     * @param playerUUID 玩家 UUID,绑定到 playerValueSql 的 ? 占位符
     * @return 通用排名查询对象
     */
    private static Query<Optional<PlayerRank>> buildRankQuery(String allValuesSql, String playerValueSql, UUID playerUUID) {
        // 拼接排名 SQL:外层 select 同时输出 rank、total、玩家自身值,便于判空
        String sql = SELECT +
                "(SELECT COUNT(*) FROM (" + allValuesSql + ") m WHERE m.val > me.val) + 1 as rank, " +
                "(SELECT COUNT(*) FROM (" + allValuesSql + ") m) as total, " +
                "me.val as player_value" +
                FROM + "(" + playerValueSql + ") me";
        // 返回 QueryStatement,绑定玩家 UUID 并解析结果
        return new QueryStatement<Optional<PlayerRank>>(sql) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                // 绑定当前玩家的 UUID,用于 me 子查询的 WHERE 过滤
                statement.setString(1, playerUUID.toString());
            }

            @Override
            public Optional<PlayerRank> processResults(ResultSet set) throws SQLException {
                // 玩家总会在 me 子查询产生一行(即使无会话,val 为 NULL)
                if (!set.next()) {
                    return Optional.empty();
                }
                // 喵~防御:若玩家没有该指标值(val 为 NULL),不给出排名,避免 0/0 或误导
                set.getLong("player_value");
                if (set.wasNull()) {
                    return Optional.empty();
                }
                // 提取 rank 与 total,组成排名结果
                return Optional.of(new PlayerRank(set.getLong("rank"), set.getLong("total")));
            }
        };
    }

    /**
     * 一个玩家的全服排名结果喵~ rank 为名次,total 为全服有该指标值的玩家总数(分母)。
     */
    public static class PlayerRank {

        private final long rank;   // 该玩家在全服的名次(从 1 开始,并列共享名次)
        private final long total;  // 全服有该指标值的玩家总数

        public PlayerRank(long rank, long total) {
            this.rank = rank;
            this.total = total;
        }

        public long getRank() {
            return rank;
        }

        public long getTotal() {
            return total;
        }
    }
}
