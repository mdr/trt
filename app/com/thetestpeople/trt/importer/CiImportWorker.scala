package com.thetestpeople.trt.importer

import com.thetestpeople.trt.model._
import com.thetestpeople.trt.model.jenkins._
import com.thetestpeople.trt.utils.CoalescingBlockingQueue
import com.thetestpeople.trt.utils.HasLogger
import com.google.inject._

@ImplementedBy(classOf[CiImportWorker])
trait CiImportQueue {

  /**
   * Enqueue the given import spec to be imported
   */
  def add(importSpecId: Id[CiImportSpec])

}

/**
 * Examine CI jobs to examine for any new builds to import.
 */
@Singleton
class CiImportWorker @Inject() (dao: CiDao, ciImporter: CiImporter) extends CiImportQueue with HasLogger {

  import dao.transaction

  private val importSpecQueue: CoalescingBlockingQueue[Id[CiImportSpec]] = new CoalescingBlockingQueue

  private var continue = true

  private var currentThread: Thread = _

  def add(importSpecId: Id[CiImportSpec]) {
    logger.debug(s"Queued import for $importSpecId")
    importSpecQueue.offer(importSpecId)
  }

  def run() {
    logger.debug(s"CI import worker started")
    currentThread = Thread.currentThread
    try
      while (continue) {
        val specIdOpt =
          try
            Some(importSpecQueue.take())
          catch {
            case e: InterruptedException ⇒
              logger.debug("CI import worker interruped")
              None
          }
        for (specId ← specIdOpt) {
          logger.info(s"Checking if there is anything to import from import spec $specId")
          try
            ciImporter.importBuilds(specId)
          catch {
            case e: Exception ⇒ logger.error(s"Problem importing from import spec $specId", e)
          }
        }
      }
    finally
      logger.debug(s"CI import worker finished")
  }

  def stop() {
    logger.debug(s"CI import worker requested to stop")
    continue = false
    currentThread.interrupt()
  }

}