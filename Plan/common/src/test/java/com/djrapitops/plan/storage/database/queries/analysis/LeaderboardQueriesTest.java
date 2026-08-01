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

import com.djrapitops.plan.delivery.domain.PlayerName;
import com.djrapitops.plan.gathering.domain.DataMap;
import com.djrapitops.plan.gathering.domain.FinishedSession;
import com.djrapitops.plan.gathering.domain.WorldTimes;
import com.djrapitops.plan.identification.Server;
import com.djrapitops.plan.identification.ServerUUID;
import com.djrapitops.plan.storage.database.DatabaseTestPreparer;
import com.djrapitops.plan.storage.database.transactions.StoreServerInformationTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreServerPlayerTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreSessionTransaction;
import org.junit.jupiter.api.Test;
import utilities.RandomData;
import utilities.TestConstants;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证 {@link LeaderboardQueries} 排行榜查询的集成测试喵~
 * <p>
 * 造两名玩家的可控会话,断言 Top N 降序排序、limit 条数生效、server 过滤生效。
 *
 * @author AuroraLS3
 */
public interface LeaderboardQueriesTest extends DatabaseTestPreparer {

    /**
     * 手工构造指定服务器上的会话,用于精确控制 playtime 喵~
     *
     * @param targetServer 会话所在服务器
     * @param playerUUID 玩家 UUID
     * @param playerName 玩家名,保证 base user 用正确名字入库
     * @param start 会话开始时间戳(毫秒)
     * @param end 会话结束时间戳(毫秒)
     * @return 组装好的已完成会话对象
     */
    default FinishedSession controlledSession(ServerUUID targetServer, UUID playerUUID, String playerName, long start, long end) {
        // 会话附加数据:玩家名与世界时长
        DataMap extraData = new DataMap();
        extraData.put(PlayerName.class, new PlayerName(playerName));
        extraData.put(WorldTimes.class, new WorldTimes());
        // 构造会话:playtime = end - start,afk 为 0
        return new FinishedSession(playerUUID, targetServer, start, end, 0L, extraData);
    }

    /**
     * 在当前服务器注册两名玩家并存储可控会话喵~ player1 playtime=3000,player2 playtime=1000。
     */
    default void storeSessionsForLeaderboardQueries() {
        // 注册两名玩家的基本资料到 plan_users 表
        db().executeTransaction(new StoreServerPlayerTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new StoreServerPlayerTransaction(player2UUID, RandomData::randomTime,
                TestConstants.PLAYER_TWO_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        // 以当前时间做基准,构造可预测长短的会话
        long now = System.currentTimeMillis();
        // player1 的会话 playtime = 3000
        db().executeTransaction(new StoreSessionTransaction(controlledSession(serverUUID(), playerUUID,
                TestConstants.PLAYER_ONE_NAME, now - 3000L, now)));
        // player2 的会话 playtime = 1000
        db().executeTransaction(new StoreSessionTransaction(controlledSession(serverUUID(), player2UUID,
                TestConstants.PLAYER_TWO_NAME, now - 1000L, now)));
    }

    @Test
    default void playtimeLeaderboardIsSortedDescending() {
        // 先造好两名玩家的会话数据
        storeSessionsForLeaderboardQueries();

        // 全网络查询,limit=10,时间窗取终身
        List<TopListQueries.TopListEntry<Long>> entries = db().query(
                LeaderboardQueries.playtimeLeaderboard(null, 10, 0L, Long.MAX_VALUE));
        // 降序第一名应是 playtime 更大的 player1
        assertEquals(TestConstants.PLAYER_ONE_NAME, entries.get(0).getPlayerName());
        assertEquals(3000L, entries.get(0).getValue());
        // 第二名是 playtime 更小的 player2
        assertEquals(TestConstants.PLAYER_TWO_NAME, entries.get(1).getPlayerName());
        assertEquals(1000L, entries.get(1).getValue());
    }

    @Test
    default void playtimeLeaderboardRespectsLimit() {
        // 先造好两名玩家的会话数据
        storeSessionsForLeaderboardQueries();

        // 只取前 1 名,应只返回 playtime 最大的 player1
        List<TopListQueries.TopListEntry<Long>> entries = db().query(
                LeaderboardQueries.playtimeLeaderboard(null, 1, 0L, Long.MAX_VALUE));
        assertEquals(1, entries.size());
        assertEquals(TestConstants.PLAYER_ONE_NAME, entries.get(0).getPlayerName());
    }

    @Test
    default void playtimeLeaderboardFiltersByServer() {
        // 用随机 UUID 造一个"另一台服务器",并注册其信息
        ServerUUID otherServer = ServerUUID.randomUUID();
        db().executeTransaction(new StoreServerInformationTransaction(
                new Server(otherServer, TestConstants.SERVER_TWO_NAME, "", TestConstants.VERSION)));
        // 注册两名玩家的基本资料到 plan_users 表
        db().executeTransaction(new StoreServerPlayerTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new StoreServerPlayerTransaction(player2UUID, RandomData::randomTime,
                TestConstants.PLAYER_TWO_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        // 以当前时间做基准,构造会话
        long now = System.currentTimeMillis();
        // player1 的会话在当前服务器,playtime = 3000
        db().executeTransaction(new StoreSessionTransaction(controlledSession(serverUUID(), playerUUID,
                TestConstants.PLAYER_ONE_NAME, now - 3000L, now)));
        // player2 的会话在另一台服务器,playtime = 9000(比 player1 大,但不在目标服务器上)
        db().executeTransaction(new StoreSessionTransaction(controlledSession(otherServer, player2UUID,
                TestConstants.PLAYER_TWO_NAME, now - 9000L, now)));

        // 只查询当前服务器,player2 在别服的会话必须被排除
        List<TopListQueries.TopListEntry<Long>> entries = db().query(
                LeaderboardQueries.playtimeLeaderboard(serverUUID(), 10, 0L, Long.MAX_VALUE));
        assertEquals(1, entries.size());
        assertEquals(TestConstants.PLAYER_ONE_NAME, entries.get(0).getPlayerName());
        assertEquals(3000L, entries.get(0).getValue());
    }
}
