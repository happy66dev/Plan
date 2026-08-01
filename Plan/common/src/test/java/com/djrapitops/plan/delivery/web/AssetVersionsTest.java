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
package com.djrapitops.plan.delivery.web;

import com.djrapitops.plan.storage.file.PlanFiles;
import com.djrapitops.plan.storage.file.Resource;
import extension.FullSystemExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import utilities.TestResources;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

/**
 * @author AuroraLS3
 */
@ExtendWith(FullSystemExtension.class)
class AssetVersionsTest {

    // This test may fail if new themes are added, but determineAssetModifications task is not run
    @Test
    void themeNamesAreCorrect(PlanFiles files) throws IOException {
        List<String> themeNames = new AssetVersions(files).getThemeNames();
        List<String> expected = getFileNamesInFolder(TestResources.getAsset("themes"))
                .filter(file -> file.endsWith("json"))
                .map(file -> file.substring(0, file.indexOf('.')))
                .sorted()
                .toList();
        assertEquals(expected, themeNames);
    }

    @Test
    void emptyAssetVersionPathDoesNotBreakLatestVersionLookup() throws IOException {
        // 喵~防御：使用独立模拟对象隔离测试资源，避免依赖真实构建产物。
        PlanFiles files = Mockito.mock(PlanFiles.class);
        // 喵~防御：模拟 AssetVersion.yml 资源，确保异常输入可重复构造。
        Resource assetVersionResource = Mockito.mock(Resource.class);
        // 喵~防御：提供空资源内容，模拟 AssetVersion.yml 没有任何有效配置路径的异常输入。
        when(assetVersionResource.asInputStream()).thenAnswer(invocation ->
                new ByteArrayInputStream(new byte[0]));
        // 喵~防御：让 AssetVersions 从模拟的 AssetVersion.yml 读取测试配置。
        when(files.getResourceFromJar("AssetVersion.yml")).thenReturn(assetVersionResource);

        AssetVersions assetVersions = new AssetVersions(files);
        // 喵~防御：空配置路径不应再触发数组越界或空值拆箱异常。
        assertDoesNotThrow(() -> assetVersions.prepare());
        // 喵~防御：没有有效资源版本时使用安全的 0 版本值。
        assertEquals(0L, assetVersions.getLatestWebAssetVersion().orElseThrow());
    }

    private Stream<String> getFileNamesInFolder(File folder) {
        return Arrays.stream(Objects.requireNonNull(folder.listFiles()))
                .filter(Objects::nonNull)
                .filter(File::isFile)
                .map(File::getName);
    }
}