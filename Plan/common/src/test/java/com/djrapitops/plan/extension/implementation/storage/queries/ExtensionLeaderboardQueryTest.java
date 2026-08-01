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

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.FormatType;
import com.djrapitops.plan.extension.annotation.BooleanProvider;
import com.djrapitops.plan.extension.annotation.DoubleProvider;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.storage.database.DatabaseTestPreparer;
import com.djrapitops.plan.storage.database.queries.analysis.TopListQueries;
import com.djrapitops.plan.storage.database.transactions.events.PlayerRegisterTransaction;
import org.junit.jupiter.api.Test;
import utilities.TestConstants;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link ExtensionLeaderboardQuery} 扩展数据排行榜的集成测试喵~
 * <p>
 * 注册自定义扩展并存储两名玩家的数值,断言降序排序、format_type 过滤、plugin 消歧、hidden 过滤。
 *
 * @author AuroraLS3
 */
public interface ExtensionLeaderboardQueryTest extends DatabaseTestPreparer {

    /**
     * 供排行榜测试使用的扩展喵~ level/ratio 可排名,year 是日期格式不可排名,secret 是隐藏布尔值。
     */
    @PluginInfo(name = "LeaderboardPlugin")
    class LeaderboardPlugin implements DataExtension {

        @NumberProvider(text = "level")
        public long level(UUID playerUUID) {
            // 让 player1 值更大(10),其余玩家较小(5),便于断言降序
            return TestConstants.PLAYER_ONE_UUID.equals(playerUUID) ? 10L : 5L;
        }

        @NumberProvider(text = "year", format = FormatType.DATE_YEAR)
        public long year(UUID playerUUID) {
            // 日期格式的值,不应出现在排行榜(被 format_type 过滤)
            return 99999L;
        }

        @DoubleProvider(text = "ratio")
        public double ratio(UUID playerUUID) {
            // 让 player1 值更大(0.8),其余玩家较小(0.2)
            return TestConstants.PLAYER_ONE_UUID.equals(playerUUID) ? 0.8 : 0.2;
        }

        @BooleanProvider(text = "secret", conditionName = "secret")
        public boolean secret(UUID playerUUID) {
            // 布尔值用作条件提供者,其值在 boolean_value 列,不在 COALESCE 数值列中,所以不会出现在排行榜喵~
            return true;
        }
    }

    /**
     * 与 {@link LeaderboardPlugin} 同名 provider "level" 的另一个扩展喵~ 用于验证 plugin 消歧。
     */
    @PluginInfo(name = "OtherLeaderboardPlugin")
    class OtherLeaderboardPlugin implements DataExtension {

        @NumberProvider(text = "level")
        public long level(UUID playerUUID) {
            // 不同插件提供的同名列,值固定为 100
            return 100L;
        }
    }

    /**
     * 注册两名玩家与扩展,并让扩展为两名玩家各生成一份数据喵~
     */
    default void registerPlayersAndExtension() {
        // 先把两名玩家注册进 plan_users 表,扩展数据才能挂到玩家 UUID 上
        db().executeTransaction(new PlayerRegisterTransaction(playerUUID, System::currentTimeMillis,
                TestConstants.PLAYER_ONE_NAME));
        db().executeTransaction(new PlayerRegisterTransaction(player2UUID, System::currentTimeMillis,
                TestConstants.PLAYER_TWO_NAME));
        // 注册 ExtensionSvc 的全局持有者,确保扩展运行时组件可用
        extensionService().register();
        // 注册测试扩展,并把两名玩家的数值存储进扩展值表
        extensionService().register(new LeaderboardPlugin());
        extensionService().updatePlayerValues(playerUUID, TestConstants.PLAYER_ONE_NAME, CallEvents.MANUAL);
        extensionService().updatePlayerValues(player2UUID, TestConstants.PLAYER_TWO_NAME, CallEvents.MANUAL);
    }

    @Test
    default void extensionLeaderboardReturnsNumberValuesSortedDescending() {
        // 先注册玩家与扩展数据
        registerPlayersAndExtension();

        // 查询 "level" 列,全网络、取前 10
        List<TopListQueries.TopListEntry<Number>> entries = db().query(
                new ExtensionLeaderboardQuery("level", "LeaderboardPlugin", null, 10));
        // 第一名应为值更大的 player1(10)
        assertEquals(TestConstants.PLAYER_ONE_NAME, entries.get(0).getPlayerName());
        assertEquals(10L, entries.get(0).getValue());
        // 第二名应为值更小的 player2(5)
        assertEquals(TestConstants.PLAYER_TWO_NAME, entries.get(1).getPlayerName());
        assertEquals(5L, entries.get(1).getValue());
    }

    @Test
    default void extensionLeaderboardSupportsDoubleValues() {
        // 先注册玩家与扩展数据
        registerPlayersAndExtension();

        // 查询 "ratio" 列,验证 double 类型也能进排行榜
        List<TopListQueries.TopListEntry<Number>> entries = db().query(
                new ExtensionLeaderboardQuery("ratio", "LeaderboardPlugin", null, 10));
        // 第一名应为 ratio 更大的 player1(0.8)
        assertEquals(TestConstants.PLAYER_ONE_NAME, entries.get(0).getPlayerName());
        // 用 delta 比较避免浮点精度误差
        assertEquals(0.8, entries.get(0).getValue().doubleValue(), 0.0001);
        assertEquals(0.2, entries.get(1).getValue().doubleValue(), 0.0001);
    }

    @Test
    default void extensionLeaderboardExcludesDateFormattedProviders() {
        // 先注册玩家与扩展数据
        registerPlayersAndExtension();

        // "year" 是 DATE_YEAR 格式,不可排名,查询应返回空列表
        List<TopListQueries.TopListEntry<Number>> entries = db().query(
                new ExtensionLeaderboardQuery("year", "LeaderboardPlugin", null, 10));
        assertTrue(entries.isEmpty(), "Date formatted providers must not be rankable");
    }

    @Test
    default void extensionLeaderboardExcludesHiddenValues() {
        // 先注册玩家与扩展数据
        registerPlayersAndExtension();

        // "secret" 是隐藏的布尔值,布尔值不在数值列中,且 hidden 过滤排除,查询应返回空列表
        List<TopListQueries.TopListEntry<Number>> entries = db().query(
                new ExtensionLeaderboardQuery("secret", "LeaderboardPlugin", null, 10));
        assertTrue(entries.isEmpty(), "Hidden values must not appear on the leaderboard");
    }

    @Test
    default void extensionLeaderboardDisambiguatesSameProviderNameAcrossPlugins() {
        // 先注册玩家与默认扩展数据
        registerPlayersAndExtension();
        // 再注册同名 provider 的另一个插件
        extensionService().register(new OtherLeaderboardPlugin());
        extensionService().updatePlayerValues(playerUUID, TestConstants.PLAYER_ONE_NAME, CallEvents.MANUAL);
        extensionService().updatePlayerValues(player2UUID, TestConstants.PLAYER_TWO_NAME, CallEvents.MANUAL);

        // 指定 LeaderboardPlugin,应只返回该插件的 level(10/5),不含 OtherLeaderboardPlugin 的 100
        List<TopListQueries.TopListEntry<Number>> leaderboardPluginEntries = db().query(
                new ExtensionLeaderboardQuery("level", "LeaderboardPlugin", null, 10));
        assertEquals(2, leaderboardPluginEntries.size());
        assertEquals(10L, leaderboardPluginEntries.get(0).getValue());
        assertEquals(5L, leaderboardPluginEntries.get(1).getValue());

        // 指定 OtherLeaderboardPlugin,应只返回该插件的 level(100)
        List<TopListQueries.TopListEntry<Number>> otherPluginEntries = db().query(
                new ExtensionLeaderboardQuery("level", "OtherLeaderboardPlugin", null, 10));
        assertEquals(2, otherPluginEntries.size());
        assertEquals(100L, otherPluginEntries.get(0).getValue());
    }

    @Test
    default void extensionLeaderboardFiltersByServer() {
        // 先注册玩家与扩展数据
        registerPlayersAndExtension();

        // 扩展值存在当前服务器的 plugin 记录下,按当前服务器过滤应能查回全部两名玩家
        List<TopListQueries.TopListEntry<Number>> entries = db().query(
                new ExtensionLeaderboardQuery("level", "LeaderboardPlugin", serverUUID(), 10));
        assertEquals(2, entries.size());
        assertEquals(TestConstants.PLAYER_ONE_NAME, entries.get(0).getPlayerName());
    }
}
