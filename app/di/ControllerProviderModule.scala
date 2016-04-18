package di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import play.api.Configuration
import controllers.Application
import play.api.Environment

class ControllerProviderModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  @Provides def controller(startup: Startup, factory: Factory): Application = factory.controller
  @Provides def webApiController(factory: Factory) = factory.webApiController
  @Provides def ciController(factory: Factory) = factory.ciController
  @Provides def jsonController(factory: Factory) = factory.jsonController
  @Provides def adminController(factory: Factory) = factory.adminController
  @Provides def testController(factory: Factory) = factory.testController
  @Provides def testsController(factory: Factory) = factory.testsController
  @Provides def batchController(factory: Factory) = factory.batchController
  @Provides def batchesController(factory: Factory) = factory.batchesController
  @Provides def executionController(factory: Factory) = factory.executionController
  @Provides def jenkinsController(factory: Factory) = factory.jenkinsController
  @Provides def teamCityController(factory: Factory) = factory.teamCityController
  @Provides def importLogController(factory: Factory) = factory.importLogController

  override def configure() {

  }

}