/*
 * SPDX-FileCopyrightText: 2017-2024 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package com.chutneytesting.junit.engine.jackson;

import com.chutneytesting.engine.api.execution.StepExecutionReportDto;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class ChutneyModule extends SimpleModule {

    private static final String NAME = "ChutneyModule";

    public ChutneyModule() {
        super(NAME);
        addSerializer(StepExecutionReportDto.class, new StepExecutionReportSerializer());
    }
}
