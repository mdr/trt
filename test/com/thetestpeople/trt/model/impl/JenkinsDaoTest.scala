package com.thetestpeople.trt.model.impl

import com.github.nscala_time.time.Imports._
import com.thetestpeople.trt.model._
import com.thetestpeople.trt.model.jenkins._
import com.thetestpeople.trt.service._
import com.thetestpeople.trt.mother.{ TestDataFactory ⇒ F }
import com.thetestpeople.trt.utils.UriUtils._
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.joda.time.DateTime
import org.joda.time.Duration
import java.net.URI

trait JenkinsDaoTest { self: AbstractDaoTest ⇒

  "Inserting and retrieving a new Jenkins import spec" should "persist all the data" in transaction { dao ⇒
    val specId = dao.newJenkinsImportSpec(F.jenkinsImportSpec(
      jobUrl = DummyData.JobUrl,
      pollingInterval = DummyData.PollingInterval,
      importConsoleLog = true,
      lastCheckedOpt = Some(DummyData.LastChecked)))

    val Some(spec) = dao.getJenkinsImportSpec(specId)
    spec.jobUrl should equal(DummyData.JobUrl)
    spec.pollingInterval should equal(DummyData.PollingInterval)
    spec.importConsoleLog should equal(true)
    spec.lastCheckedOpt should equal(Some(DummyData.LastChecked))
  }

  "Deleting a Jenkins import spec" should "delete it if present" in transaction { dao ⇒
    val specId = dao.newJenkinsImportSpec(F.jenkinsImportSpec())

    val success = dao.deleteJenkinsImportSpec(specId)

    success should be(true)
    dao.getJenkinsImportSpec(specId) should be(None)
    dao.deleteJenkinsImportSpec(specId) should be(false)
  }

  "Deleting a Jenkins import spec" should "indicate if it wasnt present to start with" in transaction { dao ⇒
    dao.deleteJenkinsImportSpec(Id.dummy) should be(false)
  }

  "Updating last checked date of an import spec" should "persist the change" in transaction { dao ⇒
    val specId = dao.newJenkinsImportSpec(F.jenkinsImportSpec(lastCheckedOpt = None))

    val success = dao.updateJenkinsImportSpec(specId, lastCheckedOpt = Some(DummyData.LastChecked))

    success should be(true)
    val Some(updatedSpec) = dao.getJenkinsImportSpec(specId)
    updatedSpec.lastCheckedOpt should be(Some(DummyData.LastChecked))
  }

  "Updating various fields of an import spec" should "persist the changes" in transaction { dao ⇒
    val specId = dao.newJenkinsImportSpec(F.jenkinsImportSpec(
      jobUrl = uri("http://www.example.com"),
      pollingInterval = 5.minutes,
      importConsoleLog = true))

    val success = dao.updateJenkinsImportSpec(F.jenkinsImportSpec(
      jobUrl = uri("http://www.elsewhere.com"),
      pollingInterval = 10.minutes,
      importConsoleLog = false).copy(id = specId))

    success should be(true)
    val Some(updatedSpec) = dao.getJenkinsImportSpec(specId)
    updatedSpec.jobUrl should be(uri("http://www.elsewhere.com"))
    updatedSpec.pollingInterval should be(10.minutes: Duration)
    updatedSpec.importConsoleLog should be(false)
  }

  "Adding a Jenkins build" should "persist all the data" in transaction { dao ⇒
    val batchId = dao.newBatch(F.batch())
    val jobId = dao.ensureJenkinsJob(F.jenkinsJob())
    val build = JenkinsBuild(
      jobId = jobId,
      batchId = batchId,
      importTime = DummyData.ImportTime,
      buildUrl = DummyData.BuildUrl)
    dao.newJenkinsBuild(build)

    val Some(buildAgain) = dao.getJenkinsBuild(DummyData.BuildUrl)
    buildAgain should equal(build)
  }

  "The DAO" should "return all the Jenkins build URLs" in transaction { dao ⇒
    def addBuild(buildUrl: URI) {
      val batchId = dao.newBatch(F.batch())
      val jobId = dao.ensureJenkinsJob(F.jenkinsJob())
      dao.newJenkinsBuild(F.jenkinsBuild(batchId, jobId = jobId, buildUrl = buildUrl))
    }
    val buildUrls = List(
      "http://www.example.com/1",
      "http://www.example.com/2",
      "http://www.example.com/3").map(uri)
    buildUrls.foreach(addBuild)

    dao.getJenkinsBuildUrls should contain theSameElementsAs (buildUrls)
  }

  "Adding a new Jenkins job" should "persist all job data" in transaction { dao ⇒
    val jobId = dao.ensureJenkinsJob(F.jenkinsJob(
      url = DummyData.JobUrl,
      name = DummyData.JobName))

    val List(job) = dao.getJenkinsJobs()
    job.id should equal(jobId)
    job.url should equal(DummyData.JobUrl)
    job.name should equal(DummyData.JobName)
  }

  "Ensuring a new Jenkins job exists" should "have no effect if it already exists" in transaction { dao ⇒
    val jobId = dao.ensureJenkinsJob(F.jenkinsJob(url = DummyData.JobUrl))
    val jobIdAgain = dao.ensureJenkinsJob(F.jenkinsJob(url = DummyData.JobUrl))
    jobIdAgain should equal(jobId)
    val List(job) = dao.getJenkinsJobs()
    job.id should equal(jobId)
  }

  "Inserting and retrieving Jenkins configuration" should "persist all the data" in transaction { dao ⇒
    val params: List[JenkinsJobParam] = List(JenkinsJobParam(
      param = DummyData.ParamName,
      value = DummyData.ParamValue))
    val config = JenkinsConfiguration(
      usernameOpt = Some(DummyData.Username),
      apiTokenOpt = Some(DummyData.ApiToken),
      rerunJobUrlOpt = Some(DummyData.JobUrl),
      authenticationTokenOpt = Some(DummyData.AuthenticationToken))
    val fullConfig = FullJenkinsConfiguration(config, params)

    dao.updateJenkinsConfiguration(fullConfig)

    val fullConfigAgain = dao.getJenkinsConfiguration
    fullConfigAgain should equal(fullConfig)
  }
}