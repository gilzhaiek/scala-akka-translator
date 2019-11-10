package com.translator.akka

import akka.actor.{Actor, ActorLogging}


object Translator {

  final case class TranslateText(requestId: Long, text: String)

  final case class OnTranslated(requestId: Long, text: String)

}

trait ActorTranslation {
  def translate(text: String): String
}

abstract class Translator(inLang: String, outLang: String) extends Actor with ActorLogging with ActorTranslation {

  import Translator._
  import TranslatorManager._

  override def preStart(): Unit = log.info("Translator actor {}->{} is now online", inLang, outLang)

  override def postStop(): Unit = log.info("Translator actor {}->{} has stopped", inLang, outLang)

  override def receive: Receive = {
    case RequestTranslator(`inLang`, `outLang`) =>
      sender() ! TranslatorRegistered

    case RequestTranslator(inLang, outLang) =>
      log.warning(
        "Ignoring TranslateText request inLang {}->{}. This actor translate {}->{}",
        inLang,
        outLang,
        this.inLang,
        this.outLang)

    case TranslateText(id, text) =>
      sender() ! OnTranslated(id, translate(text))
  }
}
