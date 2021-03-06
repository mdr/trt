package viewModel

import com.thetestpeople.trt.model._

case class TestsSummaryView(configuration: Configuration, testCounts: TestCounts) {

  val passCount = testCounts.passed

  val failCount = testCounts.failed

  val warnCount = testCounts.warning

  val ignoredCount = testCounts.ignored 
  
  val totalCount = testCounts.total

  val overallStatus: TestStatus =
    if (failCount > 0)
      TestStatus.Broken
    else if (warnCount > 0)
      TestStatus.Warning
    else
      TestStatus.Healthy

  def ballIcon: String = BallIcons.icon(overallStatus)

  def ballDescription: String = TestStatus.identifier(overallStatus)

}