package di
import com.google.inject._
import play.api.Application

import scala.concurrent.duration._
import play.api._
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits._
import com.thetestpeople.trt.utils.RichConfiguration._
import com.thetestpeople.trt.Config._
import com.thetestpeople.trt.utils.HasLogger
import scala.concurrent.Future
import play.api.mvc.WithFilters
import com.thetestpeople.trt.filters.LoggingFilter
import controllers.ControllerHelper

@Singleton
class Startup @Inject() (app: Application, factory: Factory) extends HasLogger {

  onStart(app)
  
  def onStart(app: Application) {
    logger.debug("onStart()")
    factory.dbMigrator.migrate()

    for (name ← app.configuration.getString("ui.applicationName"))
      ControllerHelper.applicationName = name

    initialiseCiImportWorker(app)
    initialiseCiImportPoller(app)
    initialiseAnalyseExecutionsPoller(app)
  }

  private def initialiseCiImportWorker(app: Application) {
    Future {
      factory.ciImportWorker.run()
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
        factory.service.syncAllCiImports()
      }
      logger.info("Initialised CI import poller")
    }
  }

  private def initialiseAnalyseExecutionsPoller(app: Application) {
    val conf = app.configuration
    val initialDelay = conf.getDuration(CountsCalculator.Poller.InitialDelay, default = 5.seconds)
    val interval = conf.getDuration(CountsCalculator.Poller.Interval, default = 2.minutes)

    Akka.system(app).scheduler.scheduleOnce(Duration.Zero) {
      factory.service.analyseAllExecutions()
    }
    Akka.system(app).scheduler.schedule(initialDelay, interval) {
      factory.service.analyseAllExecutions()
    }
    logger.info("Scheduled analysis of all executions")
  }

   def onStop(app: Application) {
    logger.debug("onStop()")
    factory.ciImportWorker.stop()
  }

}