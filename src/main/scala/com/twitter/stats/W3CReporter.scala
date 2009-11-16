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

import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import java.util.zip.CRC32
import scala.collection.Map
import scala.collection.mutable
import scala.util.Sorting._
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import net.lag.logging.Logger


/**
 */
class W3CReporter(val logger: Logger) {
  val log = Logger.get(getClass.getName)

  /**
   * The W3C header lines will be written out this often, even if the fields haven't changed.
   * (This lets log parsers resynchronize after a failure.)
   */
  var headerRepeatFrequencyInMilliseconds = 60 * 1000

  var nextHeaderDumpAt = Time.now

  private var previousCrc = 0L

  private val formatter = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss")
  formatter.setTimeZone(TimeZone.getTimeZone("GMT+0000"))

  /**
   * Write a W3C stats line to the log. If the field names differ from the previously-logged line,
   * a new header block will be written.
   */
  def report(stats: Map[String, Any]) {
    val orderedKeys = stats.keys.toList.sort(_ < _)
    val fieldsHeader = orderedKeys.mkString("#Fields: ", " ", "")
    val crc = crc32(fieldsHeader)
    if (crc != previousCrc || Time.now >= nextHeaderDumpAt) {
      logHeader(fieldsHeader, crc)
    }
    logger.info(generateLine(orderedKeys, stats))
  }

  def generateLine(orderedKeys: Iterable[String], stats: Map[String, Any]) = {
    orderedKeys.map { key => stats.get(key).map { stringify(_) }.getOrElse("-") }.mkString(" ")
  }

  private def logHeader(fieldsHeader: String, crc: Long) {
    val header =
      Array("#Version: 1.0", "\n",
            "#Date: ", formatter.format(new Date(Time.now.inMilliseconds)), "\n",
            "#CRC: ", crc.toString, "\n",
            fieldsHeader, "\n").mkString("")
    logger.info(header)
    previousCrc = crc
    nextHeaderDumpAt = headerRepeatFrequencyInMilliseconds.milliseconds.fromNow
  }

  private def crc32(header: String): Long = {
    val crc = new CRC32()
    crc.update(header.getBytes("UTF-8"))
    crc.getValue()
  }

  private def stringify(value: Any): String = value match {
    case s: String => s
    case d: Date => formatter.format(d).replaceAll(" ", "_")
    case l: Long => l.toString()
    case i: Int => i.toString()
    case ip: InetAddress => ip.getHostAddress()
    case _ => "-"
  }
}
