package com.thetestpeople.trt.service

import org.joda.time.DateTime
import com.google.inject._

@ImplementedBy(classOf[SystemClock])
trait Clock {

  def now: DateTime

}

@Singleton
class SystemClock @Inject() extends Clock {

  def now = new DateTime

}