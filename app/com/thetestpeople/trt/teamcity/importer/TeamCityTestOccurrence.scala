package com.thetestpeople.trt.teamcity.importer

import org.joda.time.Duration

case class TeamCityTestOccurrence(testName: String, status: String, detailOpt: Option[String], durationOpt: Option[Duration]) {
  
  def passed = status == "SUCCESS"
  
} 