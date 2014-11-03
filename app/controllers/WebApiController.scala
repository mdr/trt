package controllers

import com.thetestpeople.trt.json.JsonSerializers._
import com.thetestpeople.trt.model.Configuration
import com.thetestpeople.trt.service.Service
import com.thetestpeople.trt.utils.HasLogger

import play.api.libs.json.Json
import play.api.mvc.Action
import play.api.mvc.Controller

/**
 * Handle AJAX requests from the browser.
 */
class WebApiController(service: Service) extends Controller with HasLogger {

  def testNames(query: String) = Action { implicit request ⇒
    Ok(Json.toJson(service.getTestNames(query)))
  }

  def groups(query: String) = Action { implicit request ⇒
    Ok(Json.toJson(service.getGroups(query)))
  }

  def categories(query: String) = Action { implicit request ⇒
    Ok(Json.toJson(service.getCategoryNames(query)))
  }

  def configurationChart(configuration: Configuration) = Action { implicit request ⇒
    val counts = service.getHistoricalTestCounts().get(configuration).map(_.counts).getOrElse(Seq())
    Ok(Json.toJson(counts))
  }

}