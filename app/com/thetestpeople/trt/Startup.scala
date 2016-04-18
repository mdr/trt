package com.thetestpeople.trt

import scala.concurrent.Future
import scala.concurrent.duration._
import com.google.inject.ImplementedBy
import com.google.inject.Inject
import com.google.inject.Singleton
import com.thetestpeople.trt.Config.Ci
import com.thetestpeople.trt.Config.CountsCalculator
import com.thetestpeople.trt.importer.CiImportWorker
import com.thetestpeople.trt.model.impl.migration.DbMigrator
import com.thetestpeople.trt.service.Service
import com.thetestpeople.trt.service.ServiceImpl
import com.thetestpeople.trt.utils.HasLogger
import com.thetestpeople.trt.utils.RichConfiguration.RichConfig
import controllers.ControllerHelper
import play.api.Application
import play.api.Configuration
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import play.api.inject.ApplicationLifecycle

@Singleton
class Startup @Inject() (
    app: Application,
    lifecycle: ApplicationLifecycle,
    dbMigrator: DbMigrator,
    ciImportWorker: CiImportWorker,
    service: Service) extends HasLogger {

  onStart()

  lifecycle.addStopHook { () ⇒
    Future.successful(onStop())
  }

  def onStart() {
    logger.debug("onStart()")
    dbMigrator.migrate()

    for (name ← app.configuration.getString("ui.applicationName"))
      ControllerHelper.applicationName = name

    initialiseCiImportWorker(app)
    initialiseCiImportPoller(app)
    initialiseAnalyseExecutionsPoller(app)
  }

  private def initialiseCiImportWorker(app: Application) {
    Future {
      ciImportWorker.run()
    }
  }

  private def getDuration(configuration: Configuration, key: String, default: FiniteDuration): FiniteDuration =
    configuration.getMilliseconds(key).map(_.millis).getOrElse(default)

  private def initialiseCiImportPoller(app: Application) {
    val conf = app.configuration
    val initialDelay = conf.getDuration(Ci.Poller.InitialDelay, default = 1.minute)
    val interval = conf.getDuration(Ci.Poller.Interval, default = 1.minute)

    if (conf.getBoolean(Ci.Poller.Enabled).getOrElse(true)) {
      Akka.system(app).scheduler.schedule(initialDelay, interval) {
        service.syncAllCiImports()
      }
      logger.info("Initialised CI import poller")
    }
  }

  private def initialiseAnalyseExecutionsPoller(app: Application) {
    val conf = app.configuration
    val initialDelay = conf.getDuration(CountsCalculator.Poller.InitialDelay, default = 5.seconds)
    val interval = conf.getDuration(CountsCalculator.Poller.Interval, default = 2.minutes)

    Akka.system(app).scheduler.scheduleOnce(Duration.Zero) {
      service.analyseAllExecutions()
    }
    Akka.system(app).scheduler.schedule(initialDelay, interval) {
      service.analyseAllExecutions()
    }
    logger.info("Scheduled analysis of all executions")
  }

  def onStop() {
    logger.debug("onStop()")
    ciImportWorker.stop()
  }

}