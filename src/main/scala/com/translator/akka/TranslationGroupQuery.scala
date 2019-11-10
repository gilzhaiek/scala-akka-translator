package com.translator.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props, Terminated}

import scala.concurrent.duration.FiniteDuration

object TranslatorGroupQuery {

  case object CollectionTimeout

  def props(actorToOutLang: Map[ActorRef, String],
            requestId: Long,
            text: String,
            requester: ActorRef,
            timeout: FiniteDuration): Props = {
    Props(new TranslatorGroupQuery(actorToOutLang, requestId, text, requester, timeout))
  }
}

class TranslatorGroupQuery(actorToOutLang: Map[ActorRef, String],
                           requestId: Long,
                           text: String,
                           requester: ActorRef,
                           timeout: FiniteDuration) extends Actor with ActorLogging {

  import TranslatorGroupQuery._
  import context.dispatcher

  val queryTimeoutTimer: Cancellable = context.system.scheduler.scheduleOnce(timeout, self, CollectionTimeout)

  override def preStart(): Unit = {
    actorToOutLang.keysIterator.foreach { translatorActor =>
      context.watch(translatorActor)
      translatorActor ! Translator.TranslateText(0, text)
    }
  }

  override def postStop(): Unit = {
    queryTimeoutTimer.cancel()
  }

  override def receive: Receive =
    waitingForReplies(Map.empty, actorToOutLang.keySet)

  def waitingForReplies(repliesSoFar: Map[String, TranslatorGroup.TranslationResponse],
                        stillWaiting: Set[ActorRef]): Receive = {
    case Translator.OnTranslated(0, translatedText) =>
      val translatorActor = sender()
      receivedResponse(translatorActor, TranslatorGroup.Translation(translatedText), stillWaiting, repliesSoFar)

    case Terminated(translatorActor) =>
      receivedResponse(translatorActor, TranslatorGroup.TranslatorNotAvailable, stillWaiting, repliesSoFar)

    case CollectionTimeout =>
      val timedOutReplies =
        stillWaiting.map { translatorActor =>
          val translatorId = actorToOutLang(translatorActor)
          translatorId -> TranslatorGroup.TranslatorTimedOut
        }
      requester ! TranslatorGroup.RespondAllTranslations(requestId, repliesSoFar ++ timedOutReplies)
      context.stop(self)
  }

  def receivedResponse(translatorActor: ActorRef,
                       translationResponse: TranslatorGroup.TranslationResponse,
                       stillWaiting: Set[ActorRef],
                       repliesSoFar: Map[String, TranslatorGroup.TranslationResponse]): Unit = {
    context.unwatch(translatorActor)
    val outLang = actorToOutLang(translatorActor)
    val newStillWaiting = stillWaiting - translatorActor

    val newRepliesSoFar = repliesSoFar + (outLang -> translationResponse)
    if (newStillWaiting.isEmpty) {
      requester ! TranslatorGroup.RespondAllTranslations(requestId, newRepliesSoFar)
      context.stop(self)
    } else {
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
    }
  }
}
