package com.thetestpeople.trt.importer.jenkins

import org.joda.time.Duration
import org.joda.time.DateTime
import java.net.URI

case class JenkinsBuildSummary(
  url: URI,
  durationOpt: Option[Duration],
  nameOpt: Option[String],
  timestampOpt: Option[DateTime],
  resultOpt: Option[String],
  hasTestReport: Boolean,
  isBuilding: Boolean)
