/*
 * SPDX-FileCopyrightText: 2017-2026 Enedis
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */

package fr.enedis.chutney.action.common;

import fr.enedis.chutney.action.spi.injectable.Logger;

/**
 * A {@link Logger} that discards everything. Useful when reusing an action client factory outside of
 * a scenario execution (e.g. a connectivity probe), where there is no execution report to write to.
 */
public class SilentLogger implements Logger {

    @Override
    public void info(String message) {
        // no-op
    }

    @Override
    public void error(String message) {
        // no-op
    }

    @Override
    public void error(Throwable exception) {
        // no-op
    }

    @Override
    public Logger reportOnly() {
        return this;
    }
}
