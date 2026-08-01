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
import com.djrapitops.plan.gathering.domain.DeathCounter;
import com.djrapitops.plan.gathering.domain.FinishedSession;
import com.djrapitops.plan.gathering.domain.MobKillCounter;
import com.djrapitops.plan.gathering.domain.WorldTimes;
import com.djrapitops.plan.storage.database.DatabaseTestPreparer;
import com.djrapitops.plan.storage.database.transactions.events.StoreServerPlayerTransaction;
import com.djrapitops.plan.storage.database.transactions.events.StoreSessionTransaction;
import org.junit.jupiter.api.Test;
import utilities.RandomData;
import utilities.TestConstants;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link PlayerRankQueries} 排名查询的集成测试喵~
 * <p>
 * 造两名玩家的可控会话(playtime/deaths 不同),断言值大者 rank=1、小者 rank=2、total=2,
 * 以及无数据的玩家返回 empty。
 *
 * @author AuroraLS3
 */
public interface PlayerRankQueriesTest extends DatabaseTestPreparer {

    /**
     * 手工构造一个会话,用于精确控制该玩家的各项指标值喵~
     *
     * @param playerUUID 玩家 UUID
     * @param playerName 玩家名,保证 base user 用正确名字入库
     * @param start 会话开始时间戳(毫秒)
     * @param end 会话结束时间戳(毫秒)
     * @param mobKills 该会话的怪物击杀数
     * @param deaths 该会话的死亡次数
     * @return 组装好的已完成会话对象
     */
    default FinishedSession controlledSession(UUID playerUUID, String playerName, long start, long end, int mobKills, int deaths) {
        // 会话附加数据:名字、世界时长、击杀/死亡计数
        DataMap extraData = new DataMap();
        extraData.put(PlayerName.class, new PlayerName(playerName));
        extraData.put(WorldTimes.class, new WorldTimes());
        if (mobKills > 0) {
            extraData.put(MobKillCounter.class, new MobKillCounter(mobKills));
        }
        if (deaths > 0) {
            extraData.put(DeathCounter.class, new DeathCounter(deaths));
        }
        // 构造会话:playtime = end - start,afk 为 0
        return new FinishedSession(playerUUID, serverUUID(), start, end, 0L, extraData);
    }

    /**
     * 注册两名玩家并存储可控会话喵~ player1 playtime=3000/deaths=10/mobKills=5,
     * player2 playtime=1000/deaths=20/mobKills=4。
     */
    default void storeTwoPlayersWithControlledSessions() {
        // 先注册两名玩家的基本资料到 plan_users 表
        db().executeTransaction(new StoreServerPlayerTransaction(playerUUID, RandomData::randomTime,
                TestConstants.PLAYER_ONE_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        db().executeTransaction(new StoreServerPlayerTransaction(player2UUID, RandomData::randomTime,
                TestConstants.PLAYER_TWO_NAME, serverUUID(), TestConstants.GET_PLAYER_HOSTNAME));
        // 以当前时间做基准,构造可预测长短的会话
        long now = System.currentTimeMillis();
        // player1 的会话:playtime 3000、deaths 10、mobKills 5
        db().executeTransaction(new StoreSessionTransaction(controlledSession(playerUUID,
                TestConstants.PLAYER_ONE_NAME, now - 3000L, now, 5, 10)));
        // player2 的会话:playtime 1000、deaths 20、mobKills 4
        db().executeTransaction(new StoreSessionTransaction(controlledSession(player2UUID,
                TestConstants.PLAYER_TWO_NAME, now - 1000L, now, 4, 20)));
    }

    @Test
    default void playtimeRankIsOrderedByHigherPlaytime() {
        // 先造好两名玩家的会话数据
        storeTwoPlayersWithControlledSessions();

        // player1 的 playtime(3000) 大于 player2 的(1000),所以是全服第 1 名
        long playerOneRank = db().query(PlayerRankQueries.playtimeRank(playerUUID))
                .orElseThrow(AssertionError::new).getRank();
        assertEquals(1L, playerOneRank);
        // player2 的 playtime 小于 player1,所以是第 2 名
        long playerTwoRank = db().query(PlayerRankQueries.playtimeRank(player2UUID))
                .orElseThrow(AssertionError::new).getRank();
        assertEquals(2L, playerTwoRank);
        // total 是"全服有该指标值的玩家数",两名玩家都有 playtime 所以是 2
        long total = db().query(PlayerRankQueries.playtimeRank(playerUUID))
                .orElseThrow(AssertionError::new).getTotal();
        assertEquals(2L, total);
    }

    @Test
    default void deathCountRankIsOrderedByHigherDeathCount() {
        // 先造好两名玩家的会话数据
        storeTwoPlayersWithControlledSessions();

        // player2 的 deaths(20) 大于 player1 的(10),所以是全服第 1 名
        long playerTwoRank = db().query(PlayerRankQueries.deathCountRank(player2UUID))
                .orElseThrow(AssertionError::new).getRank();
        assertEquals(1L, playerTwoRank);
        // player1 的 deaths 较小,所以是第 2 名
        long playerOneRank = db().query(PlayerRankQueries.deathCountRank(playerUUID))
                .orElseThrow(AssertionError::new).getRank();
        assertEquals(2L, playerOneRank);
    }

    @Test
    default void rankIsEmptyForPlayerWithoutSessions() {
        // 只造 player1/player2 的会话,player3 没有任何数据
        storeTwoPlayersWithControlledSessions();

        // 没有会话的玩家不该有 playtime 排名,返回 empty
        Optional<PlayerRankQueries.PlayerRank> playtimeRank = db().query(PlayerRankQueries.playtimeRank(player3UUID));
        assertTrue(playtimeRank.isEmpty(), "Player without sessions should have no playtime rank");
        // 没有会话的玩家也不该有死亡排名,返回 empty
        Optional<PlayerRankQueries.PlayerRank> deathRank = db().query(PlayerRankQueries.deathCountRank(player3UUID));
        assertTrue(deathRank.isEmpty(), "Player without sessions should have no death count rank");
    }
}
