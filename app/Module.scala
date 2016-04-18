import java.io.File
import scala.concurrent.duration._
import com.google.inject._
import com.thetestpeople.trt.Config._
import com.thetestpeople.trt.model.Dao
import com.thetestpeople.trt.model.impl.SlickDao
import com.thetestpeople.trt.model.jenkins.CiDao
import com.thetestpeople.trt.service.indexing.LogIndexer
import com.thetestpeople.trt.service.indexing.LuceneLogIndexer
import com.thetestpeople.trt.utils.RichConfiguration.RichConfig
import com.thetestpeople.trt.utils.http.Http
import com.thetestpeople.trt.utils.http.PathCachingHttp
import com.thetestpeople.trt.utils.http.WsHttp
import javax.sql.DataSource
import play.api.Application
import play.api.Configuration
import play.api.Environment
import play.api.db.DB
import play.api.libs.ws.WS
import com.thetestpeople.trt.Startup

class Module(environment: Environment, configuration: Configuration) extends AbstractModule {

  @Provides @Singleton def dao(dao: SlickDao): Dao = dao

  @Provides @Singleton def ciDao(dao: SlickDao): CiDao = dao

  @Provides @Singleton def dataSource(implicit application: Application): DataSource = DB.getDataSource()
  
  @Provides @Singleton def slickDao(dataSource: DataSource): SlickDao = {
    val jdbcUrl: String = configuration.getString(Db.Default.Url).getOrElse(
      throw new RuntimeException(s"No value set for property '${Db.Default.Url}'"))

    new SlickDao(jdbcUrl, Some(dataSource))
  }

  @Provides @Singleton def logIndexer(): LogIndexer = {
    val luceneInMemory: Boolean =
      configuration.getBoolean(Lucene.InMemory).getOrElse(false)

    lazy val luceneIndexLocation: String = configuration.getString(Lucene.IndexDirectory).getOrElse(
      throw new RuntimeException(s"No value set for property '${Lucene.IndexDirectory}'"))

    if (luceneInMemory)
      LuceneLogIndexer.memoryBackedIndexer
    else
      LuceneLogIndexer.fileBackedIndexer(new File(luceneIndexLocation))
  }

  @Provides @Singleton def http(implicit application: Application): Http = {

    lazy val urlFetchTimeout: Duration =
      configuration.getDuration(Http.Timeout, default = 60.seconds)

    lazy val useCachingHttp: Boolean =
      configuration.getBoolean(Http.UseCache).getOrElse(false)

    lazy val wsClient = WS.client

    val baseHttp = new WsHttp(client = wsClient, timeout = urlFetchTimeout)
    if (useCachingHttp)
      new PathCachingHttp(baseHttp)
    else
      baseHttp
  }

  override def configure() {
    bind(classOf[Startup]).asEagerSingleton
  }

}
