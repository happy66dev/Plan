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
package com.djrapitops.plan.extension.implementation.storage.queries;

import com.djrapitops.plan.extension.FormatType;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.SQLDB;
import com.djrapitops.plan.storage.database.queries.Query;
import com.djrapitops.plan.storage.database.queries.QueryStatement;
import com.djrapitops.plan.storage.database.queries.analysis.TopListQueries;
import com.djrapitops.plan.storage.database.sql.tables.UsersTable;
import com.djrapitops.plan.storage.database.sql.tables.extension.ExtensionPlayerValueTable;
import com.djrapitops.plan.storage.database.sql.tables.extension.ExtensionPluginTable;
import com.djrapitops.plan.storage.database.sql.tables.extension.ExtensionProviderTable;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.djrapitops.plan.storage.database.sql.building.Sql.*;

/**
 * 按第三方插件某个数据列(provider)取全服玩家 Top N 的查询喵~
 * <p>
 * 数据来源:plan_extension_user_values 表,经 provider 表与 plugin 表解析出指定数据列,
 * 再用 COALESCE 把 long/double/percentage 三类数值归一成单列排序,取数值最大的若干玩家。
 *
 * @author AuroraLS3
 */
public class ExtensionLeaderboardQuery implements Query<List<TopListQueries.TopListEntry<Number>>> {

    private final String providerName; // 第三方插件数据列名(provider 方法名)
    private final String pluginName;   // 插件名,用于消歧同名 provider
    private final ServerUUID server;   // 目标服务器 UUID,为 null 表示全网络
    private final int limit;           // 返回条数上限

    /**
     * 构造扩展数据排行榜查询喵~
     *
     * @param providerName 数据列名(provider 方法名)
     * @param pluginName 插件名(用于消歧,同一数据列名可能被多个插件使用)
     * @param server 目标服务器 UUID,为 null 表示全网络
     * @param limit 返回条数上限
     */
    public ExtensionLeaderboardQuery(String providerName, String pluginName, ServerUUID server, int limit) {
        // 保存构造参数供查询使用
        this.providerName = providerName;
        this.pluginName = pluginName;
        this.server = server;
        this.limit = limit;
    }

    @Override
    public List<TopListQueries.TopListEntry<Number>> executeQuery(SQLDB db) {
        // 拼接查询:关联用户/提供者/插件表,过滤出指定数据列的可排名数值,按值降序取前 N
        String sql = SELECT + "u." + UsersTable.USER_NAME + ", " +
                "v." + ExtensionPlayerValueTable.LONG_VALUE + " as long_val, " +
                "v." + ExtensionPlayerValueTable.DOUBLE_VALUE + " as double_val, " +
                "v." + ExtensionPlayerValueTable.PERCENTAGE_VALUE + " as percentage_val, " +
                "COALESCE(v." + ExtensionPlayerValueTable.LONG_VALUE + ", v." + ExtensionPlayerValueTable.DOUBLE_VALUE + ", v." + ExtensionPlayerValueTable.PERCENTAGE_VALUE + ") as val" +
                FROM + ExtensionPlayerValueTable.TABLE_NAME + " v" +
                INNER_JOIN + ExtensionProviderTable.TABLE_NAME + " p on p." + ExtensionProviderTable.ID + "=v." + ExtensionPlayerValueTable.PROVIDER_ID +
                INNER_JOIN + ExtensionPluginTable.TABLE_NAME + " e on e." + ExtensionPluginTable.ID + "=p." + ExtensionProviderTable.PLUGIN_ID +
                INNER_JOIN + UsersTable.TABLE_NAME + " u on u." + UsersTable.USER_UUID + "=v." + ExtensionPlayerValueTable.USER_UUID +
                WHERE + "p." + ExtensionProviderTable.PROVIDER_NAME + "=?" +
                AND + "e." + ExtensionPluginTable.PLUGIN_NAME + "=?" +
                AND + "p." + ExtensionProviderTable.HIDDEN + "=?" +
                AND + "p." + ExtensionProviderTable.FORMAT_TYPE + "!=?" +
                AND + "p." + ExtensionProviderTable.FORMAT_TYPE + "!=?" +
                (server != null ? AND + "e." + ExtensionPluginTable.SERVER_UUID + "=?" : "") +
                ORDER_BY + "val DESC" + LIMIT + "?";
        // 执行查询并解析结果
        return db.query(new QueryStatement<List<TopListQueries.TopListEntry<Number>>>(sql, limit) {
            @Override
            public void prepare(PreparedStatement statement) throws SQLException {
                // 依次绑定:数据列名、插件名、是否隐藏、两个排除的时间格式、服务器 UUID(如有)、limit
                int i = 1;
                statement.setString(i++, providerName);
                statement.setString(i++, pluginName);
                statement.setBoolean(i++, false); // 只取非隐藏的 provider
                statement.setString(i++, FormatType.DATE_YEAR.name());   // 排除日期年份格式(不可排名)
                statement.setString(i++, FormatType.DATE_SECOND.name()); // 排除日期秒格式(不可排名)
                if (server != null) {
                    statement.setString(i++, server.toString());
                }
                statement.setInt(i, limit);
            }

            @Override
            public List<TopListQueries.TopListEntry<Number>> processResults(ResultSet set) throws SQLException {
                // 遍历结果集,逐个玩家组装排行榜条目,value 归一为 Number
                List<TopListQueries.TopListEntry<Number>> entries = new ArrayList<>();
                while (set.next()) {
                    // 喵~防御:读取原始数值列,优先保持数据库中的 long 类型,再回退到 double 或 percentage
                    long longValue = set.getLong("long_val");
                    boolean longValuePresent = !set.wasNull();
                    double doubleValue = set.getDouble("double_val");
                    boolean doubleValuePresent = !set.wasNull();
                    double percentageValue = set.getDouble("percentage_val");
                    boolean percentageValuePresent = !set.wasNull();
                    // 喵~防御:三类数值都为空时跳过该行,避免把 NULL 错误转换成 0
                    if (!longValuePresent && !doubleValuePresent && !percentageValuePresent) {
                        continue;
                    }
                    // 喵~防御:根据实际存储列恢复 Number 类型,整数 double 统一转换为 Long 避免 10.0
                    Number value;
                    if (longValuePresent) {
                        value = longValue;
                    } else if (doubleValuePresent) {
                        value = doubleValue == Math.floor(doubleValue) && !Double.isInfinite(doubleValue)
                                ? Long.valueOf((long) doubleValue)
                                : doubleValue;
                    } else {
                        value = percentageValue == Math.floor(percentageValue) && !Double.isInfinite(percentageValue)
                                ? Long.valueOf((long) percentageValue)
                                : percentageValue;
                    }
                    // 喵~防御:玩家名称为空时仍保留数据库行,由上层统一处理名称展示
                    entries.add(new TopListQueries.TopListEntry<>(set.getString(UsersTable.USER_NAME), value));
                }
                return entries;
            }
        });
    }
}
