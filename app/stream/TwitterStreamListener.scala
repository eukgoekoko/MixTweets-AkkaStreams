package stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorFlowMaterializer, OverflowStrategy}
import model.TweetInfo
import play.api.Logger
import twitter4j.conf.ConfigurationBuilder
import twitter4j.{Status => TwitterStatus, _}


object TwitterStreamListener {

  implicit val system = ActorSystem("mixedTweets")
  implicit val materializer = ActorFlowMaterializer()

  val searchQuery = Array("java", "scala", "haskell")

  val cb = new ConfigurationBuilder()

  cb.setDebugEnabled(true)
    .setOAuthConsumerKey("jTWONyepVvv3M38h0xH2f04N2")
    .setOAuthConsumerSecret("T6YjxKBiWSgFEgYnMfhOsE5UWUJps4VfHXn7GOroA4FcVCZldU")
    .setOAuthAccessToken("597266731-z8p4D6SVyg5v5v2wQKbfXONPjRHxKn1FxVXe5UzF")
    .setOAuthAccessTokenSecret("51HnIBEWfZ7gLLXmNNwwIRjIp6NARI5XW1sbMDAyKVc")

  val config = cb.build

  val query = new FilterQuery(0, Array[Long](), searchQuery)

  val twitterStream = new TwitterStreamFactory(config).getInstance

  def listenAndStream = {
    val (actorRef, publisher) = Source.actorRef[TweetInfo](1000, OverflowStrategy.fail).toMat(Sink.publisher)(Keep.both).run()

    val statusListener = new StatusAdapter() {

      override def onStatus(status: TwitterStatus) = {
       Logger.debug(status.getText)
       actorRef ! TweetInfo(status.getText, status.getUser.getName)
      }

      override def onException(ex: Exception) = ex.printStackTrace()

    }

    twitterStream.addListener(statusListener)
    twitterStream.filter(query)

    Source(publisher)
  }

}
