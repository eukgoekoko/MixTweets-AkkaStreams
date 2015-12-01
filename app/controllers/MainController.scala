package controllers

import akka.actor._
import akka.stream.scaladsl._
import akka.stream.{ActorFlowMaterializer, FlowMaterializer}
import model.TweetInfo
import org.reactivestreams._
import play.api.libs.EventSource
import play.api.libs.iteratee._
import play.api.libs.json.{JsValue, Json}
import play.api.libs.streams.Streams
import play.api.mvc.{Action, Controller}
import stream.TwitterStreamListener
import twitter4j.conf._


object MainController extends Controller {

  def sourceToEnumerator[Out, Mat](source: Source[Out, Mat])(implicit fm: FlowMaterializer): Enumerator[Out] = {
    val pubr: Publisher[Out] = source.runWith(Sink.publisher[Out])
    Streams.publisherToEnumerator(pubr)
  }

  val cb = new ConfigurationBuilder()

  cb.setDebugEnabled(true)
    .setOAuthConsumerKey("jTWONyepVvv3M38h0xH2f04N2")
    .setOAuthConsumerSecret("T6YjxKBiWSgFEgYnMfhOsE5UWUJps4VfHXn7GOroA4FcVCZldU")
    .setOAuthAccessToken("597266731-z8p4D6SVyg5v5v2wQKbfXONPjRHxKn1FxVXe5UzF")
    .setOAuthAccessTokenSecret("51HnIBEWfZ7gLLXmNNwwIRjIp6NARI5XW1sbMDAyKVc")

  val config = cb.build
  
  def stream(query: String) = Action {

    implicit val system = ActorSystem("mixedTweets")
    implicit val materializer = ActorFlowMaterializer()

    val toJson = (tweet: TweetInfo) => Json.obj("message" -> s"${tweet.searchQuery} : ${tweet.message}", "author" -> s"${tweet.author}")

    val queries = query.split(",")

    val streams = queries.map { query => 
      val twitterStreamListener = new TwitterStreamListener(query, config)
      twitterStreamListener.listenAndStream 
    }

    val mergedStream = Source[TweetInfo]() { implicit builder =>

      val merge = builder.add(Merge[TweetInfo](streams.length))

      for (i <- streams.indices) {
        builder.addEdge(builder.add(streams(i)), merge.in(i))
      }

      merge.out
    }

    val jsonStream = mergedStream.map(tweets => toJson(tweets))

    val jsonEumerator : Enumerator[JsValue] = sourceToEnumerator(jsonStream)

    Ok.chunked(jsonEumerator through EventSource()).as("text/event-stream")  
  } 

  def liveTweets(query: List[String]) = Action {        
    Ok(views.html.index(query))
  }

  def index = Action {
    Redirect(routes.MainController.liveTweets(List("scala", "ruby")))
  }
  
}