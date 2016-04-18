package com.thetestpeople.trt.analysis

import com.thetestpeople.trt.model._
import com.thetestpeople.trt.service.Clock
import com.thetestpeople.trt.utils.HasLogger
import com.thetestpeople.trt.utils.Utils
import com.thetestpeople.trt.utils.LockUtils._
import com.thetestpeople.trt.utils.CoalescingBlockingQueue
import java.util.concurrent._
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import com.google.inject.Singleton
import com.google.inject.Inject

/**
 * @param async -- if true, process analysis asynchronously on background worker threads. If false, perform the analysis
 *   immediately when scheduled (useful for predictable testing).
 */
@Singleton
class AnalysisService (dao: Dao, clock: Clock, async: Boolean = true) extends HasLogger {

  @Inject
  def this(dao: Dao, clock: Clock) = this(dao, clock, async = true)

  private val analysisResultLock: Lock = new ReentrantLock

  private var historicalTestCountsByConfig: Map[Configuration, HistoricalTestCountsTimeline] = Map()
  private var executionVolumeAnalysisResultOpt: Option[ExecutionVolumeAnalysisResult] = None

  /**
   * Queue of tests which need their analysis updating
   */
  private val testQueue: CoalescingBlockingQueue[Id[Test]] = new CoalescingBlockingQueue

  private def launchAnalyserThread() {
    new Thread(new Runnable() {
      def run() =
        while (true)
          handleOneQueueItem()
    }).start()
  }

  if (async)
    launchAnalyserThread()

  private def handleOneQueueItem() {
    val testId = testQueue.take()
    // logger.debug("Remaining # of tests to analyse: " + testQueue.size)
    try
      analyseTest(testId)
    catch {
      case e: Exception ⇒
        logger.error(s"Problem analysing test $testId, skipping", e)
    }
  }

  def scheduleAnalysis(testIds: Seq[Id[Test]]) =
    if (async)
      testIds.foreach(testQueue.offer)
    else
      testIds.foreach(analyseTest)

  def analyseTest(testId: Id[Test]) = dao.transaction {
    val testAnalyser = new TestAnalyser(clock, dao.getSystemConfiguration)
    val executions = dao.getExecutionsForTest(testId)
    for {
      (configuration, executionsForConfig) ← executions.groupBy(_.configuration)
      analysis ← testAnalyser.analyse(executionsForConfig)
    } updateAnalysis(testId, configuration, analysis)
  }

  private def updateAnalysis(testId: Id[Test], configuration: Configuration, analysis: TestAnalysis) {
    dao.upsertAnalysis(Analysis(
      testId = testId,
      configuration = configuration,
      status = analysis.status,
      weather = analysis.weather,
      consecutiveFailures = analysis.consecutiveFailures,
      failingSinceOpt = analysis.failingSinceOpt,
      lastPassedExecutionIdOpt = analysis.lastPassedExecutionOpt.map(_.id),
      lastPassedTimeOpt = analysis.lastPassedExecutionOpt.map(_.executionTime),
      lastFailedExecutionIdOpt = analysis.lastFailedExecutionOpt.map(_.id),
      lastFailedTimeOpt = analysis.lastFailedExecutionOpt.map(_.executionTime),
      whenAnalysed = analysis.whenAnalysed,
      medianDurationOpt = analysis.medianDurationOpt,
      lastSummaryOpt = analysis.lastSummaryOpt))
    logger.debug(s"Updated analysis for test $testId")
  }

  private def getHistoricalTestAnalyser() = {
    val systemConfiguration = dao.getSystemConfiguration
    val executionIntervalsByConfig = dao.getExecutionIntervalsByConfig
    new HistoricalTestAnalyser(executionIntervalsByConfig, systemConfiguration)
  }

  def analyseAllExecutions() = dao.transaction {
    val executionVolumeAnalyser = new ExecutionVolumeAnalyser
    val historicalTestAnalyser = getHistoricalTestAnalyser()
    dao.iterateAllExecutions { executions ⇒
      for (executionGroup ← new ExecutionGroupIterator(executions)) {
        historicalTestAnalyser.executionGroup(executionGroup)
        executionVolumeAnalyser.executionGroup(executionGroup)
      }
    }
    analysisResultLock.withLock {
      historicalTestCountsByConfig = historicalTestAnalyser.finalise
      executionVolumeAnalysisResultOpt = Some(executionVolumeAnalyser.finalise)
    }
  }

  def deleteAll() = analysisResultLock.withLock {
    logger.debug("Clearing analysis results")
    historicalTestCountsByConfig = Map()
    executionVolumeAnalysisResultOpt = None
  }

  def clearHistoricalTestCounts() = analysisResultLock.withLock {
    historicalTestCountsByConfig = Map()
  }

  def getAllHistoricalTestCounts: AllHistoricalTestCounts = analysisResultLock.withLock {
    new AllHistoricalTestCounts(historicalTestCountsByConfig)
  }

  def getExecutionVolume(configurationOpt: Option[Configuration]): Option[ExecutionVolume] = analysisResultLock.withLock {
    for {
      analysisResult ← executionVolumeAnalysisResultOpt
      volume ← analysisResult.getExecutionVolume(configurationOpt)
      if volume.countsByDate.nonEmpty
    } yield volume
  }

}