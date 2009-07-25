/** Copyright 2008 Twitter, Inc. */
package com.twitter.service

import net.lag.configgy.Configgy
import net.lag.logging.Logger
import org.specs.runner.SpecsFileRunner

object TestRunner extends SpecsFileRunner("src/test/scala/**/*.scala", ".*",
  System.getProperty("system", ".*"), System.getProperty("example", ".*")) {
  if (System.getProperty("debugtrace") == null) {
    Logger.get("").setLevel(Logger.OFF)
  }
}
