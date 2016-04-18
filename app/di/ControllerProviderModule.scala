package di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import play.api.Configuration
import controllers.Application
import play.api.Environment
import com.thetestpeople.trt.service.Service
import com.thetestpeople.trt.service.AdminService
import com.thetestpeople.trt.model.Dao
import com.thetestpeople.trt.service.indexing.LogIndexer
import com.thetestpeople.trt.analysis.AnalysisService
import com.thetestpeople.trt.service.Clock
import com.thetestpeople.trt.utils.http.Http
import com.thetestpeople.trt.importer.CiImportStatusManager
import com.thetestpeople.trt.service.BatchRecorder
import com.thetestpeople.trt.importer.CiImportQueue
import com.thetestpeople.trt.importer.CiImporter
import com.thetestpeople.trt.model.jenkins.CiDao

class ControllerProviderModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  @Provides def dao(startup: Startup, factory: Factory): Dao = factory.dao

  @Provides def ciDao(startup: Startup, factory: Factory): CiDao = factory.dao

  @Provides def logIndexer(startup: Startup, factory: Factory): LogIndexer = factory.logIndexer

  @Provides def http(startup: Startup, factory: Factory): Http = factory.http

  override def configure() {

  }

}