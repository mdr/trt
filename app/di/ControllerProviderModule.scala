package di

import com.google.inject.AbstractModule
import com.google.inject.Provides
import play.api.Configuration
import controllers.Application
import play.api.Environment

class ControllerProviderModule(environment: Environment, configuration: Configuration) extends AbstractModule {

  @Provides def controller(startup: Startup, factory: Factory): Application = factory.controller
  @Provides def webApiController(startup: Startup, factory: Factory) = factory.webApiController
  @Provides def ciController(startup: Startup, factory: Factory) = factory.ciController
  @Provides def jsonController(startup: Startup, factory: Factory) = factory.jsonController
  @Provides def adminController(startup: Startup, factory: Factory) = factory.adminController
  @Provides def testController(startup: Startup, factory: Factory) = factory.testController
  @Provides def testsController(startup: Startup, factory: Factory) = factory.testsController
  @Provides def batchController(startup: Startup, factory: Factory) = factory.batchController
  @Provides def batchesController(startup: Startup, factory: Factory) = factory.batchesController
  @Provides def executionController(startup: Startup, factory: Factory) = factory.executionController
  @Provides def jenkinsController(startup: Startup, factory: Factory) = factory.jenkinsController
  @Provides def teamCityController(startup: Startup, factory: Factory) = factory.teamCityController
  @Provides def importLogController(startup: Startup, factory: Factory) = factory.importLogController

  override def configure() {

  }

}