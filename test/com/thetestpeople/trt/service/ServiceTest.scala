package com.thetestpeople.trt.service

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest._
import com.github.nscala_time.time.Imports._
import com.thetestpeople.trt.utils._
import com.thetestpeople.trt.utils.http._
import com.thetestpeople.trt.mother.{ IncomingFactory ⇒ F }
import com.thetestpeople.trt.model.impl.MockDao
import com.thetestpeople.trt.model.impl.DummyData
import com.thetestpeople.trt.model._
import com.thetestpeople.trt.analysis.AnalysisService
import com.thetestpeople.trt.importer._
import com.thetestpeople.trt.jenkins.importer.FakeCiImportQueue
import com.thetestpeople.trt.service.indexing.LuceneLogIndexer

@RunWith(classOf[JUnitRunner])
class ServiceTest extends FlatSpec with ShouldMatchers {

  private implicit class RichService(service: Service) {

    def getStatus(testId: Id[Test]): TestStatus = {
      val Some(testAndExecutions) = service.getTestAndExecutions(testId)
      val Some(analysis) = testAndExecutions.test.analysisOpt
      analysis.status
    }

    def getEnrichedExecutionsInBatch(batchId: Id[Batch]): Seq[EnrichedExecution] =
      service.getBatchAndExecutions(batchId).toList.flatMap(_.executions)

    def getTestIdsInBatch(batchId: Id[Batch]): Seq[Id[Test]] =
      getEnrichedExecutionsInBatch(batchId).map(_.testId).distinct

    def getExecutionIdsInBatch(batchId: Id[Batch]): Seq[Id[Execution]] =
      getEnrichedExecutionsInBatch(batchId).map(_.id).distinct

  }

  "Service" should "let you add a batch" in {
    val service = setup().service
    val inBatch = F.batch()

    val batchId = service.addBatch(inBatch)

    val Seq(batch) = service.getBatches()
    batch.id should equal(batchId)
  }

  it should "allow you to fetch a batch and its executions" in {
    val service = setup().service
    val inTest = F.test(DummyData.TestName, Some(DummyData.Group))
    val inExecution = F.execution(inTest)
    val inBatch = F.batch(executions = Seq(inExecution), logOpt = Some(DummyData.Log))
    val batchId = service.addBatch(inBatch)

    val Some(EnrichedBatch(batch, Seq(execution), Some(log), None, None)) = service.getBatchAndExecutions(batchId)

    log should equal(DummyData.Log)
    execution.qualifiedName should equal(inTest.qualifiedName)
    batch.id should equal(batchId)
  }

  it should "allow you to fetch all the batches" in {
    val service = setup().service
    val inBatch1 = F.batch()
    val inBatch2 = F.batch()
    val batchId1 = service.addBatch(inBatch1)
    val batchId2 = service.addBatch(inBatch2)

    val batches = service.getBatches()

    batches.map(_.id) should contain theSameElementsAs (Seq(batchId1, batchId2))
  }

  it should "allow you to fetch a test and its executions, including analysis" in {
    val service = setup().service
    val inBatch = F.batch(executions = Seq(F.execution(F.test(), passed = true)))
    val batchId = service.addBatch(inBatch)
    val Seq(testId) = service.getTestIdsInBatch(batchId)
    val Seq(executionId) = service.getExecutionIdsInBatch(batchId)

    val Some(TestAndExecutions(testAndAnalysis, Seq(execution), configurations, _, _)) = service.getTestAndExecutions(testId)

    val EnrichedTest(test, Some(analysis), _) = testAndAnalysis
    analysis.status should equal(TestStatus.Healthy)
    testId should equal(test.id)
    executionId should equal(execution.id)
  }

  "getTests()" should "let you fetch a paged list of tests and total counts" in {
    val service = setup().service
    service.addBatch(F.batch(executions = Seq(F.execution(F.test("test1"), passed = true))))
    service.addBatch(F.batch(executions = Seq(F.execution(F.test("test2"), passed = false))))
    service.addBatch(F.batch(executions = Seq(F.execution(F.test("test3"), passed = false))))
    service.addBatch(F.batch(executions = Seq(F.execution(F.test("test4"), passed = true))))
    val TestsInfo(tests, counts, _) = service.getTests(startingFrom = 0, limit = 2)
    counts.total should be(4)
    tests.size should be(2)
  }

  "it" should "let you select just ignored tests" in {
    val service = setup().service
    val batchId = service.addBatch(F.batch(
      configurationOpt = Some(DummyData.Configuration1),
      executions = Seq(
        F.execution(F.test("test1")),
        F.execution(F.test("test2")))))
    val Seq(testId1, testId2) = service.getTestIdsInBatch(batchId)

    service.ignoreTestInConfiguration(testId1, DummyData.Configuration1)

    service.getTests(configuration = DummyData.Configuration1, ignoredOpt = None).tests.map(_.id) should contain theSameElementsAs Seq(testId1, testId2)
    service.getTests(configuration = DummyData.Configuration1, ignoredOpt = Some(true)).tests.map(_.id) should equal(Seq(testId1))
    service.getTests(configuration = DummyData.Configuration1, ignoredOpt = Some(false)).tests.map(_.id) should equal(Seq(testId2))
  }

  "Deleting batches" should "delete the data and trigger analysis" in {
    val service = setup().service
    service.updateSystemConfiguration(SystemConfiguration(
      passDurationThreshold = 0.minutes, passCountThreshold = 1,
      failureDurationThreshold = 0.minutes, failureCountThreshold = 1))

    def addBatch(passed: Boolean, executionTime: DateTime): Id[Batch] =
      service.addBatch(
        F.batch(executions = Seq(F.execution(passed = passed, executionTimeOpt = Some(executionTime)))))

    val batchId1 = addBatch(passed = true, executionTime = 2.days.ago)
    val batchId2 = addBatch(passed = false, executionTime = 1.day.ago)
    val Seq(testId) = service.getTestIdsInBatch(batchId1)

    service.getStatus(testId) should equal(TestStatus.Broken)
    service.getBatches().map(_.id) should contain theSameElementsAs Seq(batchId1, batchId2)

    service.deleteBatches(Seq(batchId2))

    service.getStatus(testId) should equal(TestStatus.Healthy)
    service.getBatches().map(_.id) should contain theSameElementsAs Seq(batchId1)
  }

  "Deleting batches" should "delete its executions from the search index" in {
    val service = setup().service
    def addBatch(): Id[Batch] =
      service.addBatch(F.batch(executions = Seq(F.execution(logOpt = Some("foo")))))
    val batchId1 = addBatch()
    val batchId2 = addBatch()

    service.searchLogs("foo")._2 should equal(2)

    service.deleteBatches(Seq(batchId2))

    service.searchLogs("foo")._2 should equal(1)
  }

  "Deleting batches" should "clean up ignore records" in {
    val service = setup().service
    val batchId = service.addBatch(F.batch(configurationOpt = Some(DummyData.Configuration1), executions = Seq(F.execution())))
    val Seq(testId) = service.getTestIdsInBatch(batchId)
    service.ignoreTestInConfiguration(testId, DummyData.Configuration1)
    service.getTests(DummyData.Configuration1).ignoredTests should equal(Seq(testId))

    service.deleteBatches(Seq(batchId))

    service.getTests(DummyData.Configuration1).ignoredTests should equal(Seq())
  }

  "Updating system configuration" should "update the status of tests" in {
    val service = setup().service
    val inTest = F.test()
    val inBatch = F.batch(executions = Seq(
      F.execution(inTest, passed = false, executionTimeOpt = Some(1.day.ago)),
      F.execution(inTest, passed = false, executionTimeOpt = Some(2.days.ago)),
      F.execution(inTest, passed = true, executionTimeOpt = Some(3.days.ago))))
    val batchId = service.addBatch(inBatch)
    val Seq(testId) = service.getTestIdsInBatch(batchId)

    val tolerantConfig = SystemConfiguration(failureDurationThreshold = 1.week.toStandardDuration, failureCountThreshold = 100)
    service.updateSystemConfiguration(tolerantConfig)
    service.getStatus(testId) should equal(TestStatus.Warning)

    val intolerantConfig = SystemConfiguration(failureDurationThreshold = 0.minutes, failureCountThreshold = 1)
    service.updateSystemConfiguration(intolerantConfig)
    service.getStatus(testId) should equal(TestStatus.Broken)
  }

  "Execution logs" should "be removed from the search index if the execution is deleted" in {
    val service = setup().service
    val batchId = service.addBatch(F.batch(executions = Seq(
      F.execution(F.test(), logOpt = Some("foo bar baz")))))
    service.searchLogs("foo")._2 should equal(1)

    service.deleteBatches(Seq(batchId))

    service.searchLogs("foo")._2 should equal(0)
  }

  "Batches" should "be able to be recorded incrementally" in {
    val service = setup().service
    val batchId = service.addBatch(F.batch(complete = false, executions = Seq(), durationOpt = None))

    {
      val batchAndExecutions = service.getBatchAndExecutions(batchId).get
      batchAndExecutions.executions.size should equal(0)
      batchAndExecutions.batch.durationOpt should equal(None)
    }

    service.addExecutionsToBatch(batchId, Seq(F.execution(F.test())))

    {
      val batchAndExecutions = service.getBatchAndExecutions(batchId).get
      batchAndExecutions.executions.size should equal(1)
      batchAndExecutions.batch.durationOpt should equal(None)
    }

    service.completeBatch(batchId, Some(DummyData.Duration))

    {
      val batchAndExecutions = service.getBatchAndExecutions(batchId).get
      batchAndExecutions.executions.size should equal(1)
      batchAndExecutions.batch.durationOpt should equal(Some(DummyData.Duration))
    }
  }

  "Batch counts and success status" should "change as executions are recorded" in {

    val service = setup().service
    val batchId = service.addBatch(F.batch(complete = false, executions = Seq(F.execution(F.test(), passed = true))))

    val batch1 = service.getBatchAndExecutions(batchId).get.batch
    batch1.passed should be(true)
    batch1.passCount should equal(1)
    batch1.failCount should equal(0)
    batch1.totalCount should equal(1)

    service.addExecutionsToBatch(batchId, Seq(F.execution(F.test(), passed = false)))

    val batch2 = service.getBatchAndExecutions(batchId).get.batch
    batch2.passed should be(false)
    batch2.passCount should equal(1)
    batch2.failCount should equal(1)
    batch2.totalCount should equal(2)

  }

  "getTests()" should "return ignored tests" in {
    val service = setup().service
    val batchId = service.addBatch(F.batch(
      configurationOpt = Some(DummyData.Configuration1),
      executions = Seq(F.execution(F.test(), passed = true))))
    val Seq(testId) = service.getTestIdsInBatch(batchId)

    val testsInfo1 = service.getTests(configuration = DummyData.Configuration1)
    testsInfo1.ignoredTests should equal(Seq())
    testsInfo1.testCounts.passed should equal(1)
    testsInfo1.testCounts.ignored should equal(0)

    service.ignoreTestInConfiguration(testId, DummyData.Configuration1)

    val testsInfo2 = service.getTests(configuration = DummyData.Configuration1)
    testsInfo2.ignoredTests should equal(Seq(testId))
    testsInfo2.testCounts.passed should equal(0)
    testsInfo2.testCounts.ignored should equal(1)

    service.unignoreTestInConfiguration(testId, DummyData.Configuration1)

    val testsInfo3 = service.getTests(configuration = DummyData.Configuration1)
    testsInfo3.ignoredTests should equal(Seq())
    testsInfo3.testCounts.passed should equal(1)
    testsInfo3.testCounts.ignored should equal(0)
  }

  "Counts of ignored tests" should "account for filters" in {
    val service = setup().service
    def addTest(group: String, name: String, ignored: Boolean): Id[Test] = {
      val batchId = service.addBatch(F.batch(
        configurationOpt = Some(DummyData.Configuration1),
        executions = Seq(F.execution(F.test(name, Some(group))))))
      val Seq(testId) = service.getTestIdsInBatch(batchId)
      if (ignored)
        service.ignoreTestInConfiguration(testId, DummyData.Configuration1)
      testId
    }
    val testId1 = addTest("A", "test1", ignored = true)
    val testId2 = addTest("A", "test2", ignored = false)
    val testId3 = addTest("B", "test3", ignored = true)

    val testCounts = service.getTests(groupOpt = Some("A"), configuration = DummyData.Configuration1).testCounts
    testCounts.ignored should equal(1)
  }

  "getTestCountsByConfiguration()" should "return test counts" in {
    val service = setup().service
    def addTest(name: String, configuration: Configuration, passed: Boolean, ignored: Boolean): Id[Test] = {
      val batchId = service.addBatch(F.batch(
        configurationOpt = Some(configuration),
        executions = Seq(F.execution(F.test(name), passed = passed))))
      val Seq(testId) = service.getTestIdsInBatch(batchId)
      if (ignored)
        service.ignoreTestInConfiguration(testId, configuration)
      testId
    }

    addTest("test1", DummyData.Configuration1, passed = true, ignored = false)
    addTest("test2", DummyData.Configuration1, passed = true, ignored = false)
    addTest("test3", DummyData.Configuration1, passed = false, ignored = false)
    addTest("test4", DummyData.Configuration1, passed = true, ignored = true)
    addTest("test5", DummyData.Configuration2, passed = true, ignored = false)

    service.getTestCountsByConfiguration() should equal(Map(
      DummyData.Configuration1 -> TestCounts(passed = 2, warning = 1, ignored = 1),
      DummyData.Configuration2 -> TestCounts(passed = 1)))
  }

  private def setup() = TestServiceFactory.setup()

}