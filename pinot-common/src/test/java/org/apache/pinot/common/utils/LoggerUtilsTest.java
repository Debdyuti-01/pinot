/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.common.utils;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


public class LoggerUtilsTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoggerUtilsTest.class);
  private static final String ROOT = "root";
  private static final String PINOT = "org.apache.pinot";

  @Test
  public void testGetAllConfiguredLoggers() {
    List<String> allLoggers = LoggerUtils.getAllConfiguredLoggers();
    assertEquals(allLoggers.size(), 2);
    assertTrue(allLoggers.contains(ROOT));
    assertTrue(allLoggers.contains(PINOT));
  }

  @Test
  public void testGetLoggerInfo() {
    Map<String, String> rootLoggerInfo = LoggerUtils.getLoggerInfo(ROOT);
    assertNotNull(rootLoggerInfo);
    assertEquals(rootLoggerInfo.get("name"), ROOT);
    assertEquals(rootLoggerInfo.get("level"), "ERROR");
    assertNull(rootLoggerInfo.get("filter"));

    Map<String, String> pinotLoggerInfo = LoggerUtils.getLoggerInfo(PINOT);
    assertNotNull(pinotLoggerInfo);
    assertEquals(pinotLoggerInfo.get("name"), PINOT);
    assertEquals(pinotLoggerInfo.get("level"), "WARN");
    assertNull(pinotLoggerInfo.get("filter"));

    assertNull(LoggerUtils.getLoggerInfo("notExistLogger"));
  }

  @Test
  public void testChangeConfiguredLoggerLevel() {
    Map<String, String> pinotLoggerInfo = LoggerUtils.getLoggerInfo(PINOT);
    assertNotNull(pinotLoggerInfo);
    assertEquals(pinotLoggerInfo.get("level"), "WARN");
    for (String level : ImmutableList.of("TRACE", "DEBUG", "INFO", "ERROR", "WARN")) {
      LoggerUtils.setLoggerLevel(PINOT, level);
      checkLogLevel(level);
      pinotLoggerInfo = LoggerUtils.getLoggerInfo(PINOT);
      assertNotNull(pinotLoggerInfo);
      assertEquals(pinotLoggerInfo.get("level"), level);
    }
  }

  @Test
  public void testChangeNonConfiguredLoggerLevel() {
    String loggerName = getClass().getCanonicalName();
    // The logger for this test class is not explicitly configured and inherits the root logger's config
    assertNull(LoggerUtils.getLoggerInfo(loggerName));

    Map<String, String> loggerInfo = LoggerUtils.setLoggerLevel(loggerName, "DEBUG");
    assertNotNull(loggerInfo);
    assertEquals(loggerInfo.get("level"), "DEBUG");

    // Verify that the logger for this test class now shows up in the configured loggers
    loggerInfo = LoggerUtils.getLoggerInfo(loggerName);
    assertNotNull(loggerInfo);
    assertEquals(loggerInfo.get("level"), "DEBUG");

    // Remove the logger configuration so that other tests aren't affected
    ((LoggerContext) LogManager.getContext(false)).getConfiguration().removeLogger(loggerName);
  }

  @Test
  public void testChangeNonConfiguredAncestorLoggerLevel() {
    String loggerName = getClass().getPackageName();
    // The logger for this package is not explicitly configured and inherits the root logger's config
    assertNull(LoggerUtils.getLoggerInfo(loggerName));

    Map<String, String> loggerInfo = LoggerUtils.setLoggerLevel(loggerName, "DEBUG");
    assertNotNull(loggerInfo);
    assertEquals(loggerInfo.get("level"), "DEBUG");

    // Verify that the logger for this package now shows up in the configured loggers
    loggerInfo = LoggerUtils.getLoggerInfo(loggerName);
    assertNotNull(loggerInfo);
    assertEquals(loggerInfo.get("level"), "DEBUG");

    // Remove the logger configuration so that other tests aren't affected
    ((LoggerContext) LogManager.getContext(false)).getConfiguration().removeLogger(loggerName);
  }

  @Test
  public void testChangeLoggerLevelWithExceptions() {
    try {
      LoggerUtils.setLoggerLevel("notExistLogger", "INFO");
      fail("Shouldn't reach here");
    } catch (RuntimeException e) {
      assertEquals(e.getMessage(), "Logger - notExistLogger not found");
    }
    try {
      LoggerUtils.setLoggerLevel(ROOT, "NotALevel");
      fail("Shouldn't reach here");
    } catch (RuntimeException e) {
      assertEquals(e.getMessage(), "Unrecognized logger level - NotALevel");
    }
  }

  private void checkLogLevel(String level) {
    switch (level) {
      case "ERROR":
        assertTrue(LOGGER.isErrorEnabled());
        assertFalse(LOGGER.isWarnEnabled());
        assertFalse(LOGGER.isInfoEnabled());
        assertFalse(LOGGER.isDebugEnabled());
        assertFalse(LOGGER.isTraceEnabled());
        break;
      case "WARN":
        assertTrue(LOGGER.isErrorEnabled());
        assertTrue(LOGGER.isWarnEnabled());
        assertFalse(LOGGER.isInfoEnabled());
        assertFalse(LOGGER.isDebugEnabled());
        assertFalse(LOGGER.isTraceEnabled());
        break;
      case "INFO":
        assertTrue(LOGGER.isErrorEnabled());
        assertTrue(LOGGER.isWarnEnabled());
        assertTrue(LOGGER.isInfoEnabled());
        assertFalse(LOGGER.isDebugEnabled());
        assertFalse(LOGGER.isTraceEnabled());
        break;
      case "DEBUG":
        assertTrue(LOGGER.isErrorEnabled());
        assertTrue(LOGGER.isWarnEnabled());
        assertTrue(LOGGER.isInfoEnabled());
        assertTrue(LOGGER.isDebugEnabled());
        assertFalse(LOGGER.isTraceEnabled());
        break;
      case "TRACE":
        assertTrue(LOGGER.isErrorEnabled());
        assertTrue(LOGGER.isWarnEnabled());
        assertTrue(LOGGER.isInfoEnabled());
        assertTrue(LOGGER.isDebugEnabled());
        assertTrue(LOGGER.isTraceEnabled());
        break;
      default:
        break;
    }
  }
}
