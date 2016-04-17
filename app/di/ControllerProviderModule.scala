package di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import play.api.Configuration
import controllers.Application
import play.api.Environment

class ControllerProviderModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  private val factory = new Factory(configuration)

  @Provides def controller: Application = factory.controller
  @Provides def webApiController = factory.webApiController
  @Provides def ciController = factory.ciController
  @Provides def jsonController = factory.jsonController
  @Provides def adminController = factory.adminController
  @Provides def testController = factory.testController
  @Provides def testsController = factory.testsController
  @Provides def batchController = factory.batchController
  @Provides def batchesController = factory.batchesController
  @Provides def executionController = factory.executionController
  @Provides def jenkinsController = factory.jenkinsController
  @Provides def teamCityController = factory.teamCityController
  @Provides def importLogController = factory.importLogController

  
  override def configure() {

  }

}