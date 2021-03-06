/*
 * Copyright 2009 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.stats

import net.lag.logging.Logger
import scala.collection.mutable
import java.util.Date
import java.util.zip.CRC32
import java.net.InetAddress
import java.text.SimpleDateFormat


/**
 * Implements a W3C Extended Log and contains convenience methods for timing blocks and
 * exporting those timings in the w3c log.
 *
 * @param fields The fields, in order, as they will appear in the final w3c log output.
 */
class W3CStats(val logger: Logger, val fields: Array[String]) extends Stats {
  val log = Logger.get

  /**
   * Store our map of named events.
   */
  private val tl = new ThreadLocal[mutable.Map[String, Any]]() {
    override def initialValue(): mutable.Map[String, Any] = new mutable.HashMap[String, Any] {
      override def initialSize = fields.length * 2
    }
  }

  /**
   * Returns the current map containing this thread's w3c logging stats.
   */
  def get(): mutable.Map[String, Any] = tl.get()

  /**
   * Resets this thread's w3c stats knowledge.
   */
  def clear(): Unit = get().clear()

  /**
   * Private method to ensure that fields being inserted are actually being tracked, throwing an exception otherwise.
   */
  private def log_safe[T](name: String, value: T) {
    if (!fields.contains(name)) {
      log.error("trying to log unregistered field: %s".format(name))
    }
    get + (name -> value)
  }

  /**
   * Adds the current name, value pair to the stats map.
   */
  def log(name: String, value: String) {
    log_safe(name, get.get(name).map(_ + "," + value).getOrElse(value))
  }

  /**
   * Adds the current name, timing pair to the stats map.
   */
  def log(name: String, timing: Long) {
    log_safe(name, get.getOrElse(name, 0L).asInstanceOf[Long] + timing)
  }

  def log(name: String, date: Date) {
    log_safe(name, date)
  }

  def log(name: String, ip: InetAddress) {
    log_safe(name, ip)
  }

  /**
   * Fetch the String formatted value from this thread's w3c map. If it doesn't exist, return
   * the ifNil string. w3c extended log says that you should use "-" for this character.
   */
  private def valueIfPresentElse(name: String, ifNil: String): String = {
    get().getOrElse(name, None) match {
      case s: String => s
      case d: Date => date_format_nospaces(d)
      case l: Long => l.toString()
      case i: Int => i.toString()
      case ip: InetAddress => ip.getHostAddress()
      case _ => ifNil
    }
  }


  /**
   * Returns a w3c logline containing all known fields.
   */
  def log_entry: String = fields.map(valueIfPresentElse(_, "-")).mkString(" ")

  /**
   * Returns an String containing W3C Headers separated by newlines.
   */
  def log_header: String = {
    val fields = fields_header()
    Array("#Version: 1.0",
          date_header(),
          crc32_header(fields_header),
          fields).mkString("\n")
  }

  /**
   * Generate a CRC32 of our current set of fields.
   */
  def crc32_header(fields_header: String): String = {
    val crc = new CRC32()
    crc.update(fields_header.getBytes("UTF-8"))
    "#CRC: %d".format(crc.getValue())
  }

  /**
   * Returns the Date Header with the current time formatted as the W3C header expects.
   */
  def date_header(): String = {
    date_header(new Date())
  }

  /**
   * Returns the Date Header with the current time formatted as the W3C header expects.
   */
  def date_header(date: Date): String = {
    "#Date: %s".format(date_format(date))
  }

  /**
   * Returns a Date formatted (without spaces) ready to insert into a w3c log line.
   */
  private def date_format_nospaces(date: Date): String = {
    date_format(date).replaceAll(" ", "_")
  }

  /**
   * Returns a tuple of (Date, Time) both formatted with a DateFormatter.
   *
   * Date is formatted as dd-MMM-yyyy (31-Dec-1969)
   * Time is formatted as HH:mm:ss (16:00:00)
   */
  def datetime_format(date: Date): (String, String) = {
    val result = date_format(date).split(" ")
    (result(0), result(1))
  }

  /**
   * Formats the supplied date
   */
  private def date_format(date: Date): String = {
    val formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
    formatter.format(date)
  }


  /**
   * Returns a W3C Fields header.
   */
  def fields_header(): String = {
    val header = new StringBuffer("#Fields: ")
    header.append(fields.mkString(" "))
    header.toString
  }

  def time[T](name: String)(f: => T): T = {
    val (rv, msec) = Stats.duration(f)
    log(name, msec)
    Stats.addTiming(name, msec.toInt)
    rv
  }

  def timeNanos[T](name: String)(f: => T): T = {
    val (rv, nsec) = Stats.durationNanos(f)
    log(name, nsec)
    Stats.addTiming(name, nsec.toInt)
    rv
  }

  def incr(name: String, count: Int) = {
    log_safe(name, get.getOrElse(name, 0L).asInstanceOf[Long] + count)
    Stats.incr(name, count)
  }

  /**
   * Coalesce all w3c events (counters, timings, etc.) that happen in this thread within this
   * transaction, and log them as a single line at the end. This is useful for logging everything
   * that happens within an HTTP request/response cycle, or similar.
   */
  def transaction[T](f: => T): T = {
    clear()
    try {
      f
    } finally {
      logger.info(log_entry)
    }
  }
}
