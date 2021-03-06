package com.thetestpeople.trt.service

import com.thetestpeople.trt.model._
import com.thetestpeople.trt.model.jenkins._
import com.thetestpeople.trt.service.jenkins.CiService
import com.thetestpeople.trt.analysis.HistoricalTestCounts
import org.joda.time.Duration
import java.net.URI
import com.thetestpeople.trt.analysis.HistoricalTestCountsTimeline
import com.thetestpeople.trt.analysis.ExecutionVolume
import com.thetestpeople.trt.analysis.ExecutionTimeMAD
import com.thetestpeople.trt.analysis.AllHistoricalTestCounts
import com.google.inject.ImplementedBy

case class TestsInfo(tests: Seq[EnrichedTest], testCounts: TestCounts, ignoredTests: Seq[Id[Test]])

case class TestAndExecutions(
  test: EnrichedTest,
  executions: Seq[EnrichedExecution],
  otherConfigurations: Seq[Configuration],
  categories: Seq[String] = Seq(),
  isIgnoredInConfiguration: Boolean)

case class ExecutionsAndTotalCount(executions: Seq[EnrichedExecution], total: Int)

/**
 * Execution and an associated search match fragment
 */
case class ExecutionAndFragment(execution: EnrichedExecution, fragment: String)

sealed trait AddCategoryResult
object AddCategoryResult {
  case object Success extends AddCategoryResult
  case object NoTestFound extends AddCategoryResult
  case object DuplicateCategory extends AddCategoryResult
}

@ImplementedBy(classOf[ServiceImpl])
trait Service extends CiService {

  def addBatch(batch: Incoming.Batch): Id[Batch]

  def addExecutionsToBatch(batchId: Id[Batch], executions: Seq[Incoming.Execution]): Boolean

  def completeBatch(batchId: Id[Batch], durationOpt: Option[Duration]): Boolean

  def getBatchAndExecutions(id: Id[Batch], passedFilterOpt: Option[Boolean] = None): Option[EnrichedBatch]

  /**
   * Return batches, ordered most recent first
   */
  def getBatches(jobOpt: Option[Id[CiJob]] = None, configurationOpt: Option[Configuration] = None, resultOpt: Option[Boolean] = None): Seq[Batch]

  def deleteBatches(batchIds: Seq[Id[Batch]])

  def getTestAndExecutions(id: Id[Test], configuration: Configuration = Configuration.Default, resultOpt: Option[Boolean] = None): Option[TestAndExecutions]

  /**
   * @param ignoredOpt -- If Some(true), return only ignored tests. If Some(false), return only tests that 
   * are not ignored. If None, return both ignored and not ignored tests.
   */
  def getTests(
    configuration: Configuration = Configuration.Default,
    testStatusOpt: Option[TestStatus] = None,
    ignoredOpt: Option[Boolean] = None,
    nameOpt: Option[String] = None,
    groupOpt: Option[String] = None,
    categoryOpt: Option[String] = None,
    startingFrom: Int = 0,
    limit: Int = Integer.MAX_VALUE,
    sortBy: SortBy.Test = SortBy.Test.Group()): TestsInfo

  def getTestCountsByConfiguration(): Map[Configuration, TestCounts]

  /**
   * @return tests that have been marked as deleted. No analysis or comment is retrieved.
   */
  def getDeletedTests(): Seq[EnrichedTest]

  def markTestsAsDeleted(ids: Seq[Id[Test]], deleted: Boolean = true)

  def getAllHistoricalTestCounts: AllHistoricalTestCounts

  def getHistoricalTestCounts(configuration: Configuration): Option[HistoricalTestCountsTimeline]

  def analyseAllExecutions()

  def getExecutions(configurationOpt: Option[Configuration], resultOpt: Option[Boolean], startingFrom: Int, limit: Int): ExecutionsAndTotalCount

  def getExecution(id: Id[Execution]): Option[EnrichedExecution]

  def getSystemConfiguration(): SystemConfiguration

  def updateSystemConfiguration(newConfig: SystemConfiguration)

  def getConfigurations(): Seq[Configuration]

  /**
   * Return true iff there is at least one execution recorded
   */
  def hasExecutions(): Boolean

  def getTestNames(pattern: String): Seq[String]

  def getGroups(pattern: String): Seq[String]

  def getCategoryNames(pattern: String): Seq[String]

  def searchLogs(query: String, startingFrom: Int = 0, limit: Int = Integer.MAX_VALUE): (Seq[ExecutionAndFragment], Int)

  def getExecutionVolume(configurationOpt: Option[Configuration]): Option[ExecutionVolume]

  def staleTests(configuration: Configuration): (Option[ExecutionTimeMAD], Seq[EnrichedTest])

  /**
   * @return true iff an execution with the given id was present in the DB
   */
  def setExecutionComment(id: Id[Execution], text: String): Boolean

  /**
   * @return true iff a batch with the given id was present in the DB
   */
  def setBatchComment(id: Id[Batch], text: String): Boolean

  /**
   * @return true iff a batch with the given id was present in the DB
   */
  def setTestComment(id: Id[Test], text: String): Boolean

  def addCategory(testId: Id[Test], category: String): AddCategoryResult

  def removeCategory(testId: Id[Test], category: String)

  /**
   * Ignore all the given test IDs in the given configuration.
   *
   * @return true iff all the given test IDs are in the DB
   */
  def ignoreTestsInConfiguration(testIds: Seq[Id[Test]], configuration: Configuration): Boolean

  def ignoreTestInConfiguration(testId: Id[Test], configuration: Configuration): Boolean =
    ignoreTestsInConfiguration(Seq(testId), configuration)

  /**
   * Stops ignoring the given test ID in the given configuration.
   *
   * @return true iff the given test ID is in the DB
   */
  def unignoreTestsInConfiguration(testIds: Seq[Id[Test]], configuration: Configuration): Boolean

  def unignoreTestInConfiguration(testId: Id[Test], configuration: Configuration): Boolean =
    unignoreTestsInConfiguration(Seq(testId), configuration)
    
}