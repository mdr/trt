package controllers

import com.thetestpeople.trt.model._
import com.thetestpeople.trt.service._
import com.thetestpeople.trt.utils.Utils
import com.thetestpeople.trt.utils.HasLogger
import com.thetestpeople.trt.model.jenkins._
import play.Logger
import play.api.mvc._
import viewModel._
import java.net.URI
import com.thetestpeople.trt.jenkins.trigger.TriggerResult
import com.google.inject.Inject

/**
 * Controller for the Tests screen.
 */
class TestsController @Inject() (service: Service) extends AbstractController(service) with RerunTestHandler with HasLogger {

  def tests(
    configurationOpt: Option[Configuration],
    testStatusOpt: Option[TestStatus],
    ignoredOpt: Option[Boolean],
    nameOpt: Option[String],
    groupOpt: Option[String],
    categoryOpt: Option[String],
    pageOpt: Option[Int],
    pageSizeOpt: Option[Int],
    sortOpt: Option[Sort],
    descendingOpt: Option[Boolean]) = Action { implicit request ⇒
    Pagination.validate(pageOpt, pageSizeOpt) match {
      case Left(errorMessage) ⇒
        BadRequest(errorMessage)
      case Right(pagination) ⇒
        configurationOpt.orElse(getDefaultConfiguration) match {
          case None ⇒
            Redirect(routes.Application.index())
          case Some(configuration) ⇒
            Ok(handleTests(testStatusOpt, ignoredOpt, configuration, nameOpt, groupOpt, categoryOpt, pagination,
              sortOpt, descendingOpt))
        }
    }
  }

  private def handleTests(
    testStatusOpt: Option[TestStatus],
    ignoredOpt: Option[Boolean],
    configuration: Configuration,
    nameOpt: Option[String],
    groupOpt: Option[String],
    categoryOpt: Option[String],
    pagination: Pagination,
    sortOpt: Option[Sort],
    descendingOpt: Option[Boolean])(implicit request: Request[_]) = {
    val sortBy = SortHelper.getTestSortBy(sortOpt, descendingOpt)
    val TestsInfo(tests, testCounts, ignoredTests) = service.getTests(
      configuration = configuration,
      testStatusOpt = testStatusOpt,
      ignoredOpt = ignoredOpt,
      nameOpt = nameOpt,
      groupOpt = groupOpt,
      categoryOpt = categoryOpt,
      startingFrom = pagination.firstItem,
      limit = pagination.pageSize,
      sortBy = sortBy)

    val testViews = tests.map(t ⇒ TestView(t, isIgnoredInConfiguration = ignoredTests contains t.id))
    val testsSummary = TestsSummaryView(configuration, testCounts)

    val paginationData = pagination.paginationData(itemCount(testCounts, testStatusOpt, ignoredOpt))
    views.html.tests(testsSummary, testViews, configuration, testStatusOpt, ignoredOpt, nameOpt, groupOpt, categoryOpt,
      service.canRerun, paginationData, sortOpt, descendingOpt)
  }

  def itemCount(testCounts: TestCounts, testStatusOpt: Option[TestStatus], ignoredOpt: Option[Boolean]) =
    if (ignoredOpt == Some(true))
      testCounts.ignored
    else testStatusOpt match {
      case Some(TestStatus.Healthy) ⇒ testCounts.passed
      case Some(TestStatus.Warning) ⇒ testCounts.warning
      case Some(TestStatus.Broken)  ⇒ testCounts.failed
      case None                     ⇒ testCounts.total
    }

  private def selectedTestIds(implicit request: Request[AnyContent]): Seq[Id[Test]] =
    getFormParameters("selectedTest").flatMap(Id.parse[Test])

  def deleteTests() = Action { implicit request ⇒
    service.markTestsAsDeleted(selectedTestIds)
    Redirect(previousUrlOrDefault).flashing("success" -> "Marked tests as deleted.")
  }

  def undeleteTests() = Action { implicit request ⇒
    service.markTestsAsDeleted(selectedTestIds, deleted = false)
    Redirect(previousUrlOrDefault).flashing("success" -> "Marked tests as no longer deleted.")
  }

  def rerunSelectedTests() = Action { implicit request ⇒
    rerunTests(selectedTestIds)
  }

  def ignoreTests(configuration: Configuration) = Action { implicit request ⇒
    val success = service.ignoreTestsInConfiguration(selectedTestIds, configuration)
    if (success)
      Redirect(previousUrlOrDefault).flashing("success" -> s"Ignored tests in configuration $configuration.")
    else
      Redirect(previousUrlOrDefault).flashing("error" -> "Problem ignoring tests.")
  }

  def unignoreTests(configuration: Configuration) = Action { implicit request ⇒
    val success = service.unignoreTestsInConfiguration(selectedTestIds, configuration)
    if (success)
      Redirect(previousUrlOrDefault).flashing("success" -> s"Stopped ignoring tests in configuration $configuration.")
    else
      Redirect(previousUrlOrDefault).flashing("error" -> "Problem ignoring tests.")
  }

}