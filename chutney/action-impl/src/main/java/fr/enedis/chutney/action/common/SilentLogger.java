/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import fr.enedis.chutney.action.spi.injectable.Logger;

/**
 * A {@link Logger} that discards everything. Connectivity probes reuse action connection code that
 * expects a scenario logger, but a probe has no scenario report to write to — its outcome is the
 * returned verdict, not log lines.
 */
public final class SilentLogger implements Logger {

    @Override
    public void info(String message) {
    }

    @Override
    public void error(String message) {
    }

    @Override
    public void error(Throwable exception) {
    }

    @Override
    public Logger reportOnly() {
        return this;
    }
}
