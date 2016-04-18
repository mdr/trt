package com.thetestpeople.trt.service

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[AdminServiceImpl])
trait AdminService {

  def deleteAll()

  def analyseAll()

}