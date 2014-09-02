package com.thetestpeople.trt.jenkins.importer

import org.joda.time.Duration
import org.joda.time.DateTime
import scala.xml.Elem
import scala.xml.Node
import java.net.URI

class JenkinsBuildXmlParser {

  @throws[ParseException]
  def parseBuild(elem: Elem): BuildSummary = {
    val url = getFieldOpt(elem, "url").getOrElse(
      throw new ParseException("Could not find a <url> element"))
    val durationOpt = getFieldOpt(elem, "duration").map(parseLong).map(Duration.millis)
    val nameOpt = getFieldOpt(elem, "fullDisplayName")
    val timestampOpt = getFieldOpt(elem, "timestamp").map(parseLong).map(new DateTime(_))
    val result = getFieldOpt(elem, "result").getOrElse(
      throw new ParseException("Could not find a <result> element"))
    val hasTestReport = (elem \ "action" \ "urlName").exists(_.text == "testReport")
    BuildSummary(
      url = new URI(url),
      durationOpt = durationOpt,
      nameOpt = nameOpt,
      timestampOpt = timestampOpt,
      result = result,
      hasTestReport = hasTestReport)
  }

  private def getFieldOpt(node: Node, name: String): Option[String] =
    (node \ name).headOption.map(_.text)

  private def parseLong(s: String): Long =
    try s.toLong
    catch {
      case e: NumberFormatException ⇒
        throw ParseException(s"Cannot parse '$s' as an integer", e)
    }

}