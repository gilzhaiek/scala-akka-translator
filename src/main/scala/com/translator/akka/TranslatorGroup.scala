package com.translator.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.concurrent.duration._

object TranslatorGroup {
  def props(inLang: String): Props = Props(new TranslatorGroup(inLang))

  final case class RequestTranslatorList(requestId: Long)

  final case class ReplyTranslatorList(requestId: Long, ids: Set[String])

  final case class RequestAllTranslations(requestId: Long, text: String)

  final case class RespondAllTranslations(requestId: Long, translations: Map[String, TranslationResponse])

  sealed trait TranslationResponse

  final case class Translation(text: String) extends TranslationResponse

  case object TranslationNotAvailable extends TranslationResponse

  case object TranslatorNotAvailable extends TranslationResponse

  case object TranslatorTimedOut extends TranslationResponse

}

class TranslatorGroup(inLang: String) extends Actor with ActorLogging {

  import TranslatorGroup._
  import TranslatorManager._

  var supportedTranslations = Seq.empty[String]
  var outLangToActor = Map.empty[String, ActorRef]
  var actorToOutLang = Map.empty[ActorRef, String]

  override def preStart(): Unit = {
    log.info("TranslatorGroup {} started", inLang)
    supportedTranslations = YandexTranslator.getLangs(inLang).map { case (langCode, _) => langCode }.toList
  }

  override def postStop(): Unit = log.info("TranslatorGroup {} stopped", inLang)

  override def receive: Receive = {
    case translateTextMsg@RequestTranslator(`inLang`, _) =>
      outLangToActor.get(translateTextMsg.outLang) match {
        case Some(translatorActor) =>
          translatorActor.forward(translateTextMsg)
        case None =>
          var actorRefOption: Option[ActorRef] = None
          if (translateTextMsg.outLang == inLang.reverse) {
            log.info("Creating reverse translator actor for {}", translateTextMsg.outLang)
            actorRefOption = Some(context.actorOf(ReverseTranslator.props(inLang, translateTextMsg.outLang), s"ReverseTranslator-${inLang}-${translateTextMsg.outLang}"))
          }
          else if (supportedTranslations.contains(translateTextMsg.outLang)) {
            log.info("Creating yandex translator actor for {}", translateTextMsg.outLang)
            actorRefOption = Option(context.actorOf(YandexTranslator.props(inLang, translateTextMsg.outLang), s"YandexTranslator-${inLang}-${translateTextMsg.outLang}"))
          }

          actorRefOption match {
            case Some(translatorActor) =>
              context.watch(translatorActor)
              actorToOutLang += translatorActor -> translateTextMsg.outLang
              outLangToActor += translateTextMsg.outLang -> translatorActor
              translatorActor.forward(translateTextMsg)
            case None =>
              log.warning("No support for {}->{} translation", inLang, translateTextMsg.outLang)
              sender() ! TranslatorNotSupported
          }
      }

    case RequestTranslator(inLang, _) =>
      log.warning("Ignoring TranslateText request for {}. This actor is responsible for {}.", inLang, this.inLang)

    case RequestTranslatorList(requestId) =>
      sender() ! ReplyTranslatorList(requestId, outLangToActor.keySet)

    case RequestAllTranslations(requestId, text) =>
      context.actorOf(
        TranslatorGroupQuery
          .props(actorToOutLang = actorToOutLang, requestId = requestId, text = text, requester = sender(), 3.seconds))

    case Terminated(translatorActor) =>
      val translatorId = actorToOutLang(translatorActor)
      log.info("Translator actor for {} has been terminated", translatorId)
      actorToOutLang -= translatorActor
      outLangToActor -= translatorId
  }
}