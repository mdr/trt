package com.thetestpeople.trt.scripts

import com.thetestpeople.trt.importer.teamcity.TeamCityUrlParser
import com.thetestpeople.trt.utils.UriUtils._
import com.thetestpeople.trt.importer.teamcity.TeamCityBuildDownloader
import play.api.libs.ws.ning.NingWSClient
import com.ning.http.client.AsyncHttpClientConfig
import com.thetestpeople.trt.utils.http.WsHttp
import com.thetestpeople.trt.importer.teamcity.TeamCityBatchCreator
import com.thetestpeople.trt.model.Configuration
import play.api.libs.json._
import com.thetestpeople.trt.json.JsonSerializers._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._

object ScrapeTeamCity extends App {

    val Right(jobLink) = TeamCityUrlParser.parse(uri("http://localhost:8111/viewType.html?buildTypeId=TestReportyThing_Build"))
//  val Right(configuration) = TeamCityUrlParser.parse(uri("https://teamcity.jetbrains.com/viewType.html?buildTypeId=NetCommunityProjects_Femah_Commit"))

  val wsClient = new NingWSClient(new AsyncHttpClientConfig.Builder().build())
  val http = new WsHttp(wsClient)
  val downloader = new TeamCityBuildDownloader(http, jobLink)

  val (buildType, buildLinks) = downloader.getBuildType()
  for (link ← buildLinks) {
    val build = downloader.getBuild(link)

    val batchCreator = new TeamCityBatchCreator(Some(Configuration.Default))
    val batch = batchCreator.createBatch(build)

    val batchJson = Json.toJson(batch)

    val future = wsClient.url("http://localhost:9000/api/batches").post(batchJson)
    val response = Await.result(future, 600.seconds)
    assert(response.status == 200)
  }
}