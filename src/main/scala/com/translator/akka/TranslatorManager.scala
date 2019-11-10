package com.translator.akka

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

object TranslatorManager {
  def props(): Props = Props(new TranslatorManager)

  final case class RequestTranslator(inLang: String, outLang: String)

  case object TranslatorRegistered

  case object TranslatorNotSupported

}

class TranslatorManager extends Actor with ActorLogging {

  import TranslatorManager._

  var inLangToActor = Map.empty[String, ActorRef]
  var actorToInLang = Map.empty[ActorRef, String]

  override def preStart(): Unit = log.info("TranslatorManager started")

  override def postStop(): Unit = log.info("TranslatorManager stopped")

  override def receive = {
    case trackMsg@RequestTranslator(inLang, _) =>
      inLangToActor.get(inLang) match {
        case Some(ref) =>
          ref.forward(trackMsg)
        case None =>
          log.info("Creating from group actor for {}", inLang)
          val groupActor = context.actorOf(TranslatorGroup.props(inLang), "in-" + inLang)
          context.watch(groupActor)
          groupActor.forward(trackMsg)
          inLangToActor += inLang -> groupActor
          actorToInLang += groupActor -> inLang
      }

    case Terminated(groupActor) =>
      val inLang = actorToInLang(groupActor)
      log.info("Translator group actor for {} has been terminated", inLang)
      actorToInLang -= groupActor
      inLangToActor -= inLang

  }
}