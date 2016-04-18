package com.thetestpeople.trt.model

import com.thetestpeople.trt.model.jenkins.CiDao
import com.google.inject.ImplementedBy
import com.thetestpeople.trt.model.impl.SlickDao

@ImplementedBy(classOf[SlickDao])
trait Dao extends ExecutionDao with CiDao with BatchDao with TestDao {

  /**
   * Run the given block within a transaction
   */
  def transaction[T](block: ⇒ T): T

  def getSystemConfiguration(): SystemConfiguration

  def updateSystemConfiguration(newConfig: SystemConfiguration)

  def getConfigurations(): Seq[Configuration]

  def deleteAll()
  
}
