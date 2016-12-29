package com.rusabakumov.util

import com.typesafe.scalalogging.{Logger => ScalaLogger}
import org.slf4j.LoggerFactory

trait Logging {
  private val sl4jLogger = LoggerFactory.getLogger(this.getClass)
  protected val log = ScalaLogger(sl4jLogger)
}
