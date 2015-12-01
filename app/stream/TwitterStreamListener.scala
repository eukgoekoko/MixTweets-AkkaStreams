package stream

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorFlowMaterializer, OverflowStrategy}
import model.TweetInfo
import play.api.Logger
import twitter4j._
import twitter4j.conf.Configuration
import twitter4j.{Status => TwitterStatus}


/**
 * @author Evgeniy Muravev
 */
class TwitterStreamListener(searchQuery: String, config: Configuration) {

  implicit val system = ActorSystem("mixedTweets")
  implicit val materializer = ActorFlowMaterializer()

  val query = new FilterQuery(0, Array(), Array(searchQuery))

  val twitterStream = new TwitterStreamFactory(config).getInstance

  def listenAndStream = {

    val (actorRef, publisher) =  Source.actorRef[TweetInfo](1000, OverflowStrategy.fail).toMat(Sink.publisher)(Keep.both).run()

    Logger.info(s"#start listener for $searchQuery")

    val statusListener = new StatusListener() {

      override def onStatus(status: TwitterStatus) = {
       Logger.debug(status.getText)
       //push elements into a publisher
       actorRef ! TweetInfo(searchQuery, status.getText, status.getUser.getName)
      }

      override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = {}

      override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = {}

      override def onException(ex: Exception) = ex.printStackTrace()

      override def onScrubGeo(userId: Long, upToStatusId: Long) = {}

      override def onStallWarning(warning: StallWarning) = {}

    }

    twitterStream.addListener(statusListener)
    twitterStream.filter(query)

    Source(publisher)
  }

}